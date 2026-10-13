package com.merrycoral.checktodo.data

import androidx.room.Embedded
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

/**
 * 예약된(아직 울리지 않은) 알람 기록. 알람이 울리면 지운다.
 * 재부팅 후 다시 등록하고, 꺼져 있던 동안 놓친 알림(기록이 남아 있는데 시각이 지난 것)을 찾는 데 쓴다.
 * - [snooze]: false = 정규 알람, true = '1시간 후 다시알림'
 * - [epochDay]: 이 알림이 가리키는 회차의 날짜
 * - [triggerAt]: 알람 시각(epoch millis)
 */
@Entity(
    tableName = "pending_alarms",
    primaryKeys = ["taskId", "snooze"],
    foreignKeys = [
        ForeignKey(
            entity = RoutineTask::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PendingAlarm(
    val taskId: Long,
    val snooze: Boolean,
    val epochDay: Long,
    val triggerAt: Long,
)

/** 할 일 + 소속 카테고리 이름·색. 한 번의 JOIN 쿼리로 읽어 둘이 항상 일관된다. */
data class TaskWithCategory(
    @Embedded val task: RoutineTask,
    val categoryName: String,
    val categoryColor: Int,
)
