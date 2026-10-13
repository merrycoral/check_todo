package com.merrycoral.checktodo.data

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.room.withTransaction
import com.merrycoral.checktodo.schedule.AlarmScheduler
import com.merrycoral.checktodo.schedule.NotificationHelper
import com.merrycoral.checktodo.widget.TodoWidget
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

private const val SNOOZE_MILLIS = 60 * 60 * 1000L

class Repository(
    private val context: Context,
    private val db: AppDatabase,
    private val scheduler: AlarmScheduler,
) {
    fun observeTasks(): Flow<List<RoutineTask>> = db.taskDao().observeAll()
    fun observeTasksWithCategory(): Flow<List<TaskWithCategory>> = db.taskDao().observeAllWithCategory()
    fun observeCategories(): Flow<List<Category>> = db.categoryDao().observeAll()
    fun observeLogs(epochDay: Long): Flow<List<TaskLog>> = db.logDao().observeByDay(epochDay)

    fun canScheduleExactAlarms(): Boolean = scheduler.canScheduleExact()

    /** 홈 화면 위젯용: [today] 의 할 일을 한 번 읽어온다. */
    suspend fun loadToday(today: LocalDate): List<TodayItem> = buildTodayItems(
        tasks = db.taskDao().getAllWithCategory(),
        logs = db.logDao().getByDay(today.toEpochDay()),
        today = today,
    )

    suspend fun addCategory(name: String, color: Int): Long =
        db.categoryDao().insert(Category(name = name, color = color))

    suspend fun setCategoryColor(categoryId: Long, color: Int) {
        db.categoryDao().updateColor(categoryId, color)
        refreshWidgets()
    }

    suspend fun saveTask(task: RoutineTask) {
        val saved = if (task.id == 0L) task.copy(id = db.taskDao().insert(task)) else task.also { db.taskDao().update(it) }
        planRegular(saved)
        refreshWidgets()
    }

    suspend fun setEnabled(task: RoutineTask, enabled: Boolean) {
        val updated = task.copy(enabled = enabled)
        db.taskDao().update(updated)
        if (!enabled) NotificationHelper.cancel(context, task.id)
        planRegular(updated)
        refreshWidgets()
    }

    suspend fun deleteTask(task: RoutineTask) {
        scheduler.cancelAll(task.id)
        NotificationHelper.cancel(context, task.id)
        db.taskDao().delete(task) // pending_alarms, task_logs 는 외래키 CASCADE 로 함께 삭제
        refreshWidgets()
    }

    /** 이미 삭제된 할 일(오래된 알림·위젯의 버튼)에 대한 기록 요청은 할 일이 없으므로 무시한다. */
    suspend fun setLog(taskId: Long, epochDay: Long, status: LogStatus) {
        if (db.taskDao().getById(taskId) == null) return
        db.logDao().upsert(TaskLog(taskId, epochDay, status, System.currentTimeMillis()))
        NotificationHelper.cancel(context, taskId)
        refreshWidgets()
    }

    suspend fun clearLog(taskId: Long, epochDay: Long) {
        db.logDao().delete(taskId, epochDay)
        refreshWidgets()
    }

    /**
     * 현재 회차를 1시간 뒤에 다시 알린다. 기록을 DB 에 남기므로 재부팅 후에도 복구되고,
     * 정확한 알람 권한이 없어 지금 등록하지 못해도 권한 허용 후 다시 등록된다.
     */
    suspend fun snooze(taskId: Long, epochDay: Long) {
        if (db.taskDao().getById(taskId) == null) return
        val alarm = PendingAlarm(taskId, snooze = true, epochDay = epochDay, triggerAt = System.currentTimeMillis() + SNOOZE_MILLIS)
        db.pendingAlarmDao().upsert(alarm)
        scheduler.set(alarm.taskId, alarm.snooze, alarm.epochDay, alarm.triggerAt)
        NotificationHelper.cancel(context, taskId)
    }

    /**
     * 알람을 모두 맞춘다(앱 실행·재부팅·시간 변경·권한 변경 시 호출, 멱등).
     * - 아직 울릴 시각이 안 된 다시알림은 시스템에 다시 등록한다.
     * - 시각이 지났는데 기록이 남아 있는 알람은 울리지 못한 것이다(기기가 꺼져 있었던 경우 등).
     *   오늘 회차라면 한 번 알리고, 날짜가 지난 회차는 알리지 않고 버린다.
     * - 사용 중인 모든 할 일의 다음 정규 알람을 다시 계산해 등록한다.
     */
    suspend fun rescheduleAll() {
        val now = System.currentTimeMillis()
        val today = LocalDate.now().toEpochDay()
        val tasks = db.taskDao().getAll()
        val tasksById = tasks.associateBy { it.id }
        db.pendingAlarmDao().getAll().forEach { alarm ->
            val task = tasksById[alarm.taskId] ?: return@forEach
            if (alarm.triggerAt > now) {
                if (alarm.snooze) scheduler.set(alarm.taskId, true, alarm.epochDay, alarm.triggerAt)
            } else {
                db.pendingAlarmDao().delete(alarm.taskId, alarm.snooze)
                if (alarm.epochDay == today) notifyIfPending(task, alarm.epochDay)
            }
        }
        tasks.forEach { planRegular(it) }
        refreshWidgets()
    }

    /**
     * 알람이 울렸을 때. 기록(pending_alarms)이 없거나 다른 회차를 가리키면 이미 처리된 알람이므로 무시한다
     * (예: 앱 실행 시 '놓친 알림'으로 먼저 처리된 뒤 지연 도착한 알람).
     */
    suspend fun onAlarmFired(taskId: Long, epochDay: Long, isSnooze: Boolean) {
        val alarm = db.pendingAlarmDao().get(taskId, isSnooze)
        if (alarm == null || alarm.epochDay != epochDay) return
        db.pendingAlarmDao().delete(taskId, isSnooze)
        val task = db.taskDao().getById(taskId) ?: return
        if (!isSnooze) planRegular(task)
        refreshWidgets() // 날짜가 바뀐 뒤 첫 알람에서 위젯이 새 날짜로 갱신된다
        notifyIfPending(task, epochDay)
    }

    /** 모든 데이터를 한 시점의 스냅샷으로 JSON 문자열로 만든다. */
    suspend fun exportBackup(): String {
        val snapshot = db.withTransaction {
            BackupSnapshot(
                categories = db.categoryDao().getAll(),
                tasks = db.taskDao().getAll(),
                logs = db.logDao().getAll(),
            )
        }
        return BackupCodec.encode(snapshot, System.currentTimeMillis())
    }

    /**
     * 현재 데이터를 모두 지우고 [snapshot] 으로 교체한다(한 트랜잭션, 실패 시 기존 데이터 유지).
     * 이전 할 일의 알람·알림은 해제하고, 새 데이터 기준으로 알람과 위젯을 다시 맞춘다.
     */
    suspend fun restoreBackup(snapshot: BackupSnapshot) {
        val previousTasks = db.taskDao().getAll()
        db.withTransaction {
            db.logDao().deleteAll()
            db.taskDao().deleteAll() // pending_alarms 도 CASCADE 로 함께 삭제
            db.categoryDao().deleteAll()
            db.categoryDao().insertAll(snapshot.categories)
            db.taskDao().insertAll(snapshot.tasks)
            db.logDao().insertAll(snapshot.logs)
        }
        previousTasks.forEach {
            scheduler.cancelAll(it.id)
            NotificationHelper.cancel(context, it.id)
        }
        rescheduleAll()
    }

    /** 다음 정규 알람을 계산해 기록하고 등록한다. 사용 중이 아닌 할 일은 모든 알람을 해제한다. */
    private suspend fun planRegular(task: RoutineTask) {
        if (!task.enabled) {
            db.pendingAlarmDao().deleteForTask(task.id)
            scheduler.cancelAll(task.id)
            return
        }
        val next = task.nextTrigger(LocalDateTime.now())
        val alarm = PendingAlarm(
            taskId = task.id,
            snooze = false,
            epochDay = next.toLocalDate().toEpochDay(),
            triggerAt = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        )
        db.pendingAlarmDao().upsert(alarm)
        scheduler.set(alarm.taskId, alarm.snooze, alarm.epochDay, alarm.triggerAt)
    }

    /** 사용 중이고 아직 처리(수행/건너뛰기)하지 않은 회차라면 알림을 띄운다. */
    private suspend fun notifyIfPending(task: RoutineTask, epochDay: Long) {
        if (!task.enabled) return
        if (db.logDao().get(task.id, epochDay) != null) return
        val category = db.categoryDao().getById(task.categoryId)
        NotificationHelper.show(context, task, category.name, epochDay)
    }

    private suspend fun refreshWidgets() = TodoWidget().updateAll(context)
}
