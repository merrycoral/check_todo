package com.merrycoral.checktodo.ui

import android.app.Application
import android.net.Uri
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.merrycoral.checktodo.CheckTodoApp
import com.merrycoral.checktodo.data.BackupCodec
import com.merrycoral.checktodo.data.BackupSnapshot
import com.merrycoral.checktodo.data.Category
import com.merrycoral.checktodo.data.LogStatus
import com.merrycoral.checktodo.data.RoutineTask
import com.merrycoral.checktodo.data.TaskWithCategory
import com.merrycoral.checktodo.data.TodayItem
import com.merrycoral.checktodo.data.buildTodayItems
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = (app as CheckTodoApp).repository

    private val _today = MutableStateFlow(LocalDate.now())
    val today: StateFlow<LocalDate> = _today

    private val _exactAlarmAllowed = MutableStateFlow(repository.canScheduleExactAlarms())
    val exactAlarmAllowed: StateFlow<Boolean> = _exactAlarmAllowed

    private val _notificationsAllowed = MutableStateFlow(true)
    val notificationsAllowed: StateFlow<Boolean> = _notificationsAllowed

    private val _backupMessage = MutableStateFlow<String?>(null)
    val backupMessage: StateFlow<String?> = _backupMessage

    /** 복원 확인 대기 중인 백업 내용. null 이면 대기 중인 복원이 없다. */
    private val _pendingRestore = MutableStateFlow<BackupSnapshot?>(null)
    val pendingRestore: StateFlow<BackupSnapshot?> = _pendingRestore

    val categories: StateFlow<List<Category>> = repository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 아직 DB 에서 읽지 못했으면 null. (빈 목록과 구분해, 로드 전에 편집기가 빈 폼으로 열리지 않게 한다) */
    val tasks: StateFlow<List<RoutineTask>?> = repository.observeTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // 할 일과 카테고리는 JOIN 쿼리 하나로 읽는다. 따로 읽으면 백업 복원처럼 둘이 동시에 바뀔 때
    // 서로 다른 시점의 목록이 합쳐져 존재하지 않는 카테고리를 참조할 수 있다.
    val taskRows: StateFlow<List<TaskWithCategory>> = repository.observeTasksWithCategory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val todayLogs = _today.flatMapLatest { repository.observeLogs(it.toEpochDay()) }

    val todayItems: StateFlow<List<TodayItem>> =
        combine(repository.observeTasksWithCategory(), todayLogs, _today) { tasks, logs, today ->
            buildTodayItems(tasks, logs, today)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // 앱을 켜 둔 채 자정이 지나도 '오늘'이 바뀌도록 한다.
        viewModelScope.launch {
            while (true) {
                val now = LocalDateTime.now()
                val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay()
                delay(Duration.between(now, nextMidnight).toMillis() + 1_000)
                _today.value = LocalDate.now()
            }
        }
    }

    /** 화면이 다시 보일 때: 날짜·권한 상태를 갱신하고 알람을 다시 맞춘다(멱등). */
    fun onResume() {
        _today.value = LocalDate.now()
        _exactAlarmAllowed.value = repository.canScheduleExactAlarms()
        _notificationsAllowed.value = NotificationManagerCompat.from(getApplication()).areNotificationsEnabled()
        viewModelScope.launch { repository.rescheduleAll() }
    }

    fun setDone(item: TodayItem) = setLog(item, LogStatus.DONE)

    fun skip(item: TodayItem) = setLog(item, LogStatus.SKIPPED)

    fun undo(item: TodayItem) {
        viewModelScope.launch { repository.clearLog(item.task.id, _today.value.toEpochDay(), item.slot) }
    }

    private fun setLog(item: TodayItem, status: LogStatus) {
        viewModelScope.launch { repository.setLog(item.task.id, _today.value.toEpochDay(), item.slot, status) }
    }

    fun saveTask(task: RoutineTask) {
        viewModelScope.launch { repository.saveTask(task) }
    }

    fun deleteTask(task: RoutineTask) {
        viewModelScope.launch { repository.deleteTask(task) }
    }

    fun setEnabled(task: RoutineTask, enabled: Boolean) {
        viewModelScope.launch { repository.setEnabled(task, enabled) }
    }

    fun addCategory(name: String, color: Int, onAdded: (Long) -> Unit) {
        viewModelScope.launch { onAdded(repository.addCategory(name, color)) }
    }

    fun setCategoryColor(category: Category, color: Int) {
        viewModelScope.launch { repository.setCategoryColor(category.id, color) }
    }

    /** 모든 데이터를 JSON 으로 [uri] 에 저장한다. */
    fun exportBackup(uri: Uri) = launchBackup("백업 저장 실패") {
        val json = repository.exportBackup()
        val output = getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
            ?: error("파일을 열 수 없어요.")
        output.use { it.write(json.toByteArray(Charsets.UTF_8)) }
        "백업 파일을 저장했어요."
    }

    /** [uri] 의 백업 파일을 읽어 검증한 뒤, 사용자 확인을 기다린다(아직 데이터는 바뀌지 않음). */
    fun prepareRestore(uri: Uri) = launchBackup("백업 파일 확인 실패") {
        val input = getApplication<Application>().contentResolver.openInputStream(uri)
            ?: error("파일을 열 수 없어요.")
        val text = input.use { it.readBytes().toString(Charsets.UTF_8) }
        _pendingRestore.value = BackupCodec.decode(text)
        null
    }

    fun confirmRestore() {
        val snapshot = _pendingRestore.value ?: return
        _pendingRestore.value = null
        launchBackup("복원 실패") {
            repository.restoreBackup(snapshot)
            "백업에서 복원했어요."
        }
    }

    fun cancelRestore() {
        _pendingRestore.value = null
    }

    /** 백업/복원 작업을 실행하고, 성공 메시지(없으면 null) 또는 실패 사유를 화면에 알린다. */
    private fun launchBackup(failurePrefix: String, block: suspend () -> String?) {
        viewModelScope.launch {
            _backupMessage.value = try {
                withContext(Dispatchers.IO) { block() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "$failurePrefix: ${e.message}"
            }
        }
    }
}
