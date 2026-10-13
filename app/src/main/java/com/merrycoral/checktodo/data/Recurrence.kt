package com.merrycoral.checktodo.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

val WEEKDAY_LABELS = listOf("월", "화", "수", "목", "금", "토", "일")

private const val MAX_LOOKAHEAD_DAYS = 370L

fun weekdayBit(day: DayOfWeek): Int = 1 shl (day.value - 1)

/** [date] 에 이 할 일이 발생하는지. 매월 N일이 없는 달(예: 31일)은 말일에 발생한다. */
fun RoutineTask.occursOn(date: LocalDate): Boolean = when (repeatType) {
    RepeatType.DAILY -> true
    RepeatType.WEEKLY -> weekDays and weekdayBit(date.dayOfWeek) != 0
    RepeatType.MONTHLY -> date.dayOfMonth == minOf(monthDay, date.lengthOfMonth())
}

/** [after] 이후(엄밀히 초과)의 가장 가까운 알림 시각. */
fun RoutineTask.nextTrigger(after: LocalDateTime): LocalDateTime {
    val startDate = after.toLocalDate()
    for (offset in 0..MAX_LOOKAHEAD_DAYS) {
        val date = startDate.plusDays(offset)
        if (!occursOn(date)) continue
        val candidate = date.atTime(hour, minute)
        if (candidate.isAfter(after)) return candidate
    }
    error("다음 알림 시각을 찾을 수 없음: $this")
}

fun RoutineTask.repeatLabel(): String = when (repeatType) {
    RepeatType.DAILY -> "매일"
    RepeatType.WEEKLY -> WEEKDAY_LABELS.filterIndexed { i, _ -> weekDays and (1 shl i) != 0 }
        .joinToString("·", prefix = "매주 ")
    RepeatType.MONTHLY -> "매월 ${monthDay}일"
}

fun RoutineTask.timeLabel(): String = "%02d:%02d".format(hour, minute)
