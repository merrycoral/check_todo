package com.merrycoral.checktodo.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

private const val TASK_WITH_CATEGORY_QUERY =
    "SELECT t.*, c.name AS categoryName, c.color AS categoryColor " +
        "FROM tasks t JOIN categories c ON c.id = t.categoryId ORDER BY t.id"

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY id")
    fun observeAll(): Flow<List<RoutineTask>>

    @Query(TASK_WITH_CATEGORY_QUERY)
    fun observeAllWithCategory(): Flow<List<TaskWithCategory>>

    @Query(TASK_WITH_CATEGORY_QUERY)
    suspend fun getAllWithCategory(): List<TaskWithCategory>

    @Query("SELECT * FROM tasks ORDER BY hour, minute, id")
    suspend fun getAll(): List<RoutineTask>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): RoutineTask?

    @Insert
    suspend fun insert(task: RoutineTask): Long

    @Insert
    suspend fun insertAll(tasks: List<RoutineTask>)

    @Query("DELETE FROM tasks")
    suspend fun deleteAll()

    @Update
    suspend fun update(task: RoutineTask)

    @Delete
    suspend fun delete(task: RoutineTask)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY id")
    fun observeAll(): Flow<List<Category>>

    @Query("SELECT * FROM categories ORDER BY id")
    suspend fun getAll(): List<Category>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Long): Category

    @Query("UPDATE categories SET color = :color WHERE id = :id")
    suspend fun updateColor(id: Long, color: Int)

    @Insert
    suspend fun insert(category: Category): Long

    @Insert
    suspend fun insertAll(categories: List<Category>)

    @Query("DELETE FROM categories")
    suspend fun deleteAll()
}

@Dao
interface LogDao {
    @Query("SELECT * FROM task_logs WHERE epochDay = :epochDay")
    fun observeByDay(epochDay: Long): Flow<List<TaskLog>>

    @Query("SELECT * FROM task_logs")
    suspend fun getAll(): List<TaskLog>

    @Insert
    suspend fun insertAll(logs: List<TaskLog>)

    @Query("DELETE FROM task_logs")
    suspend fun deleteAll()

    @Query("SELECT * FROM task_logs WHERE epochDay = :epochDay")
    suspend fun getByDay(epochDay: Long): List<TaskLog>

    @Query("SELECT * FROM task_logs WHERE taskId = :taskId AND epochDay = :epochDay AND slot = :slot")
    suspend fun get(taskId: Long, epochDay: Long, slot: Int): TaskLog?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(log: TaskLog)

    @Query("DELETE FROM task_logs WHERE taskId = :taskId AND epochDay = :epochDay AND slot = :slot")
    suspend fun delete(taskId: Long, epochDay: Long, slot: Int)
}

@Dao
interface PendingAlarmDao {
    @Query("SELECT * FROM pending_alarms")
    suspend fun getAll(): List<PendingAlarm>

    @Query("SELECT * FROM pending_alarms WHERE taskId = :taskId")
    suspend fun getForTask(taskId: Long): List<PendingAlarm>

    @Query("SELECT * FROM pending_alarms WHERE taskId = :taskId AND snooze = :snooze AND slot = :slot")
    suspend fun get(taskId: Long, snooze: Boolean, slot: Int): PendingAlarm?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(alarm: PendingAlarm)

    @Query("DELETE FROM pending_alarms WHERE taskId = :taskId AND snooze = :snooze AND slot = :slot")
    suspend fun delete(taskId: Long, snooze: Boolean, slot: Int)

    /** 할 일의 정규 알람 기록을 지운다(정규 알람은 할 일마다 하나만 둔다). */
    @Query("DELETE FROM pending_alarms WHERE taskId = :taskId AND snooze = 0")
    suspend fun deleteRegular(taskId: Long)

    @Query("DELETE FROM pending_alarms WHERE taskId = :taskId")
    suspend fun deleteForTask(taskId: Long)
}
