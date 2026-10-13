package com.merrycoral.checktodo.ui

import android.app.Application
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.merrycoral.checktodo.CheckTodoApp
import com.merrycoral.checktodo.data.Category
import com.merrycoral.checktodo.data.LogStatus
import com.merrycoral.checktodo.data.RoutineTask
import com.merrycoral.checktodo.data.TodayItem
import com.merrycoral.checktodo.data.buildTodayItems
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** 할 일 관리 목록의 한 줄. */
data class TaskRow(
    val task: RoutineTask,
    val categoryName: String,
    val categoryColor: Int,
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = (app as CheckTodoApp).repository

    private val _today = MutableStateFlow(LocalDate.now())
    val today: StateFlow<LocalDate> = _today

    private val _exactAlarmAllowed = MutableStateFlow(repository.canScheduleExactAlarms())
    val exactAlarmAllowed: StateFlow<Boolean> = _exactAlarmAllowed

    private val _notificationsAllowed = MutableStateFlow(true)
    val notificationsAllowed: StateFlow<Boolean> = _notificationsAllowed

    val categories: StateFlow<List<Category>> = repository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tasks: StateFlow<List<RoutineTask>> = repository.observeTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val todayLogs = _today.flatMapLatest { repository.observeLogs(it.toEpochDay()) }

    // tasks 와 categories 는 각각 따로 로드되므로, 둘 다 DB 에서 읽힌 뒤에만 이름을 매핑한다.
    // (초기값을 가진 StateFlow 를 combine 하면 카테고리보다 할 일이 먼저 도착할 수 있다)
    val taskRows: StateFlow<List<TaskRow>> =
        combine(repository.observeTasks(), repository.observeCategories()) { tasks, categories ->
            val categoryById = categories.associateBy { it.id }
            tasks.map {
                val category = categoryById.getValue(it.categoryId)
                TaskRow(it, category.name, category.color)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val todayItems: StateFlow<List<TodayItem>> =
        combine(repository.observeTasks(), repository.observeCategories(), todayLogs, _today) { tasks, categories, logs, today ->
            buildTodayItems(tasks, categories, logs, today)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
        viewModelScope.launch { repository.clearLog(item.task.id, _today.value.toEpochDay()) }
    }

    private fun setLog(item: TodayItem, status: LogStatus) {
        viewModelScope.launch { repository.setLog(item.task.id, _today.value.toEpochDay(), status) }
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
}
