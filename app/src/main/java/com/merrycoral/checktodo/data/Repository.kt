package com.merrycoral.checktodo.data

import android.content.Context
import com.merrycoral.checktodo.schedule.AlarmScheduler
import com.merrycoral.checktodo.schedule.NotificationHelper
import kotlinx.coroutines.flow.Flow

class Repository(
    private val context: Context,
    private val db: AppDatabase,
    private val scheduler: AlarmScheduler,
) {
    fun observeTasks(): Flow<List<RoutineTask>> = db.taskDao().observeAll()
    fun observeCategories(): Flow<List<Category>> = db.categoryDao().observeAll()
    fun observeLogs(epochDay: Long): Flow<List<TaskLog>> = db.logDao().observeByDay(epochDay)

    fun canScheduleExactAlarms(): Boolean = scheduler.canScheduleExact()

    suspend fun addCategory(name: String): Long = db.categoryDao().insert(Category(name = name))

    suspend fun saveTask(task: RoutineTask) {
        val saved = if (task.id == 0L) task.copy(id = db.taskDao().insert(task)) else task.also { db.taskDao().update(it) }
        scheduler.schedule(saved)
    }

    suspend fun setEnabled(task: RoutineTask, enabled: Boolean) {
        val updated = task.copy(enabled = enabled)
        db.taskDao().update(updated)
        if (!enabled) {
            scheduler.cancel(task.id)
            NotificationHelper.cancel(context, task.id)
        }
        scheduler.schedule(updated)
    }

    suspend fun deleteTask(task: RoutineTask) {
        scheduler.cancel(task.id)
        NotificationHelper.cancel(context, task.id)
        db.taskDao().delete(task)
    }

    suspend fun setLog(taskId: Long, epochDay: Long, status: LogStatus) {
        db.logDao().upsert(TaskLog(taskId, epochDay, status, System.currentTimeMillis()))
        NotificationHelper.cancel(context, taskId)
    }

    suspend fun clearLog(taskId: Long, epochDay: Long) {
        db.logDao().delete(taskId, epochDay)
    }

    suspend fun snooze(taskId: Long, epochDay: Long) {
        scheduler.scheduleSnooze(taskId, epochDay)
        NotificationHelper.cancel(context, taskId)
    }

    suspend fun rescheduleAll() {
        db.taskDao().getAll().forEach(scheduler::schedule)
    }

    /** 알람이 울렸을 때: 다음 회차를 예약하고, 아직 처리하지 않았다면 알림을 띄운다. */
    suspend fun onAlarmFired(taskId: Long, epochDay: Long, isSnooze: Boolean) {
        val task = db.taskDao().getById(taskId) ?: return
        if (!isSnooze) scheduler.schedule(task)
        if (!task.enabled) return
        if (db.logDao().get(taskId, epochDay) != null) return
        val category = db.categoryDao().getById(task.categoryId)
        NotificationHelper.show(context, task, category.name, epochDay)
    }
}
