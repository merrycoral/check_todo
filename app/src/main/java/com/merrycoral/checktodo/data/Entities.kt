package com.merrycoral.checktodo.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class RepeatType { DAILY, WEEKLY, MONTHLY }

enum class LogStatus { DONE, SKIPPED }

@Entity(tableName = "categories", indices = [Index(value = ["name"], unique = true)])
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** ARGB */
    val color: Int,
)

/**
 * 반복 할 일.
 * - [weekDays]: 월=bit0 … 일=bit6 (WEEKLY 에서만 사용)
 * - [monthDay]: 1..31 (MONTHLY 에서만 사용, 해당 일이 없는 달은 말일)
 */
@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("categoryId")],
)
data class RoutineTask(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val categoryId: Long,
    val repeatType: RepeatType,
    val weekDays: Int = 0,
    val monthDay: Int = 1,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean = true,
)

/** 특정 날짜(epochDay)의 회차에 대한 수행/건너뛰기 기록. 기록이 없으면 미처리. */
@Entity(
    tableName = "task_logs",
    primaryKeys = ["taskId", "epochDay"],
    foreignKeys = [
        ForeignKey(
            entity = RoutineTask::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class TaskLog(
    val taskId: Long,
    val epochDay: Long,
    val status: LogStatus,
    val loggedAt: Long,
)
