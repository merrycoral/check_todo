package com.merrycoral.checktodo.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY hour, minute, id")
    fun observeAll(): Flow<List<RoutineTask>>

    @Query("SELECT * FROM tasks")
    suspend fun getAll(): List<RoutineTask>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): RoutineTask?

    @Insert
    suspend fun insert(task: RoutineTask): Long

    @Update
    suspend fun update(task: RoutineTask)

    @Delete
    suspend fun delete(task: RoutineTask)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY id")
    fun observeAll(): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Long): Category

    @Insert
    suspend fun insert(category: Category): Long
}

@Dao
interface LogDao {
    @Query("SELECT * FROM task_logs WHERE epochDay = :epochDay")
    fun observeByDay(epochDay: Long): Flow<List<TaskLog>>

    @Query("SELECT * FROM task_logs WHERE taskId = :taskId AND epochDay = :epochDay")
    suspend fun get(taskId: Long, epochDay: Long): TaskLog?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(log: TaskLog)

    @Query("DELETE FROM task_logs WHERE taskId = :taskId AND epochDay = :epochDay")
    suspend fun delete(taskId: Long, epochDay: Long)
}
