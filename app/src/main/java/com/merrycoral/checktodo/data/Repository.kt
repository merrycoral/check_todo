package com.merrycoral.checktodo.data

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.room.withTransaction
import com.merrycoral.checktodo.schedule.AlarmScheduler
import com.merrycoral.checktodo.schedule.NotificationHelper
import com.merrycoral.checktodo.widget.TodoWidget
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class Repository(
    private val context: Context,
    private val db: AppDatabase,
    private val scheduler: AlarmScheduler,
) {
    fun observeTasks(): Flow<List<RoutineTask>> = db.taskDao().observeAll()
    fun observeCategories(): Flow<List<Category>> = db.categoryDao().observeAll()
    fun observeLogs(epochDay: Long): Flow<List<TaskLog>> = db.logDao().observeByDay(epochDay)

    fun canScheduleExactAlarms(): Boolean = scheduler.canScheduleExact()

    /** 홈 화면 위젯용: [today] 의 할 일을 한 번 읽어온다. */
    suspend fun loadToday(today: LocalDate): List<TodayItem> = buildTodayItems(
        tasks = db.taskDao().getAll(),
        categories = db.categoryDao().getAll(),
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
        scheduler.schedule(saved)
        refreshWidgets()
    }

    suspend fun setEnabled(task: RoutineTask, enabled: Boolean) {
        val updated = task.copy(enabled = enabled)
        db.taskDao().update(updated)
        if (!enabled) {
            scheduler.cancel(task.id)
            NotificationHelper.cancel(context, task.id)
        }
        scheduler.schedule(updated)
        refreshWidgets()
    }

    suspend fun deleteTask(task: RoutineTask) {
        scheduler.cancel(task.id)
        NotificationHelper.cancel(context, task.id)
        db.taskDao().delete(task)
        refreshWidgets()
    }

    suspend fun setLog(taskId: Long, epochDay: Long, status: LogStatus) {
        db.logDao().upsert(TaskLog(taskId, epochDay, status, System.currentTimeMillis()))
        NotificationHelper.cancel(context, taskId)
        refreshWidgets()
    }

    suspend fun clearLog(taskId: Long, epochDay: Long) {
        db.logDao().delete(taskId, epochDay)
        refreshWidgets()
    }

    suspend fun snooze(taskId: Long, epochDay: Long) {
        scheduler.scheduleSnooze(taskId, epochDay)
        NotificationHelper.cancel(context, taskId)
    }

    /** 알람을 다시 맞추고 위젯도 갱신한다(앱 실행·재부팅·시간 변경 시 호출). */
    suspend fun rescheduleAll() {
        db.taskDao().getAll().forEach(scheduler::schedule)
        refreshWidgets()
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
            db.taskDao().deleteAll()
            db.categoryDao().deleteAll()
            db.categoryDao().insertAll(snapshot.categories)
            db.taskDao().insertAll(snapshot.tasks)
            db.logDao().insertAll(snapshot.logs)
        }
        previousTasks.forEach {
            scheduler.cancel(it.id)
            NotificationHelper.cancel(context, it.id)
        }
        rescheduleAll()
    }

    /** 알람이 울렸을 때: 다음 회차를 예약하고, 아직 처리하지 않았다면 알림을 띄운다. */
    suspend fun onAlarmFired(taskId: Long, epochDay: Long, isSnooze: Boolean) {
        val task = db.taskDao().getById(taskId) ?: return
        if (!isSnooze) scheduler.schedule(task)
        refreshWidgets() // 날짜가 바뀐 뒤 첫 알람에서 위젯이 새 날짜로 갱신된다
        if (!task.enabled) return
        if (db.logDao().get(taskId, epochDay) != null) return
        val category = db.categoryDao().getById(task.categoryId)
        NotificationHelper.show(context, task, category.name, epochDay)
    }

    private suspend fun refreshWidgets() = TodoWidget().updateAll(context)
}
