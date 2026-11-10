package com.merrycoral.checktodo.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

/**
 * 회차의 상태.
 * - [PENDING]: 오늘, 시각은 지났지만 아직 기록하지 않음 (아직 할 수 있음)
 * - [MISSED]: 지난 날, 끝내 기록하지 않음
 * - [UPCOMING]: 아직 시각이 되지 않음
 */
enum class OccurrenceState { DONE, SKIPPED, PENDING, MISSED, UPCOMING }

/** 특정 날짜의 회차 하나와 그 상태 */
data class DayOccurrence(
    val task: RoutineTask,
    val slot: Int,
    val categoryName: String,
    val categoryColor: Int,
    val state: OccurrenceState,
)

/** 하루의 회차 상태별 개수 */
data class DaySummary(
    val date: LocalDate,
    val done: Int,
    val skipped: Int,
    val pending: Int,
    val missed: Int,
    val upcoming: Int,
) {
    /** 시각이 지났거나 이미 기록한 회차 수 (수행률의 분모) */
    val due: Int get() = done + skipped + pending + missed

    /** 그날의 회차를 하나도 놓치지 않고 모두 처리(수행 또는 건너뛰기)했는지 */
    val isComplete: Boolean get() = due > 0 && pending == 0 && missed == 0 && upcoming == 0
}

/** 한 달 동안 할 일 하나의 기록 */
data class TaskStat(
    val task: TaskWithCategory,
    val done: Int,
    val skipped: Int,
    /** 놓침 + 오늘 아직 안 한 회차 */
    val notDone: Int,
) {
    val due: Int get() = done + skipped + notDone
    val rate: Float get() = if (due == 0) 0f else done.toFloat() / due
}

data class MonthStats(
    val month: YearMonth,
    /** 1일부터 말일까지 */
    val days: List<DaySummary>,
    /** 오늘까지 놓친 회차 없이 이어진 날 수 */
    val streak: Int,
    /** 이번 달에 해야 할 회차가 있었던 할 일, 수행률이 낮은 순 */
    val perTask: List<TaskStat>,
    /** 기록을 볼 수 있는 가장 이른 달 (가장 먼저 만든 할 일의 시작 달) */
    val firstMonth: YearMonth,
) {
    val done: Int get() = days.sumOf { it.done }
    val skipped: Int get() = days.sumOf { it.skipped }
    val missed: Int get() = days.sumOf { it.missed }
    val due: Int get() = days.sumOf { it.due }
    val rate: Float get() = if (due == 0) 0f else done.toFloat() / due
}

/** (할 일, 날짜, 시각) → 기록 상태 조회용 색인 */
class LogIndex(logs: List<TaskLog>) {
    private val statusByOccurrence = logs.associate { Triple(it.taskId, it.epochDay, it.slot) to it.status }

    fun status(taskId: Long, epochDay: Long, slot: Int): LogStatus? = statusByOccurrence[Triple(taskId, epochDay, slot)]
}

/**
 * [date] 의 회차와 상태를 시각 → 할 일 순으로 돌려준다.
 * 지금의 할 일 설정(반복·알림 시각·사용 여부)을 기준으로 계산하므로, 설정을 바꾸면 지난 날의 회차도 바뀐 기준으로 보인다.
 * 사용 중이 아닌 할 일과 시작일 이전의 날은 제외한다.
 */
fun occurrencesOn(date: LocalDate, tasks: List<TaskWithCategory>, logs: LogIndex, now: LocalDateTime): List<DayOccurrence> {
    val epochDay = date.toEpochDay()
    return tasks
        .filter { it.task.enabled && epochDay >= it.task.startEpochDay && it.task.occursOn(date) }
        .flatMap { row ->
            row.task.times.map { slot ->
                DayOccurrence(row.task, slot, row.categoryName, row.categoryColor, stateOf(row.task.id, date, slot, logs, now))
            }
        }
        .sortedWith(compareBy({ it.slot }, { it.task.id }))
}

private fun stateOf(taskId: Long, date: LocalDate, slot: Int, logs: LogIndex, now: LocalDateTime): OccurrenceState =
    when (logs.status(taskId, date.toEpochDay(), slot)) {
        LogStatus.DONE -> OccurrenceState.DONE
        LogStatus.SKIPPED -> OccurrenceState.SKIPPED
        null -> {
            val today = now.toLocalDate()
            when {
                date < today -> OccurrenceState.MISSED
                date > today -> OccurrenceState.UPCOMING
                date.atTime(slot / 60, slot % 60) <= now -> OccurrenceState.PENDING
                else -> OccurrenceState.UPCOMING
            }
        }
    }

fun summarize(date: LocalDate, occurrences: List<DayOccurrence>): DaySummary {
    val counts = occurrences.groupingBy { it.state }.eachCount()
    return DaySummary(
        date = date,
        done = counts[OccurrenceState.DONE] ?: 0,
        skipped = counts[OccurrenceState.SKIPPED] ?: 0,
        pending = counts[OccurrenceState.PENDING] ?: 0,
        missed = counts[OccurrenceState.MISSED] ?: 0,
        upcoming = counts[OccurrenceState.UPCOMING] ?: 0,
    )
}

/**
 * 오늘까지 놓친 회차 없이 이어진 날 수. 해야 할 회차가 없던 날은 건너뛴다(끊기지도 세지도 않음).
 * 오늘은 모두 처리했을 때만 세고, 아직 진행 중이면 어제부터 센다.
 */
fun currentStreak(tasks: List<TaskWithCategory>, logs: LogIndex, now: LocalDateTime): Int {
    val earliest = tasks.filter { it.task.enabled }.minOfOrNull { it.task.startEpochDay } ?: return 0
    val today = now.toLocalDate()
    var streak = if (summarize(today, occurrencesOn(today, tasks, logs, now)).isComplete) 1 else 0
    var date = today.minusDays(1)
    while (date.toEpochDay() >= earliest) {
        val summary = summarize(date, occurrencesOn(date, tasks, logs, now))
        if (summary.due > 0) {
            if (!summary.isComplete) break
            streak++
        }
        date = date.minusDays(1)
    }
    return streak
}

fun buildMonthStats(month: YearMonth, tasks: List<TaskWithCategory>, logs: List<TaskLog>, now: LocalDateTime): MonthStats {
    val index = LogIndex(logs)
    val occurrencesByDay = (1..month.lengthOfMonth()).map { day ->
        val date = month.atDay(day)
        date to occurrencesOn(date, tasks, index, now)
    }
    val perTask = occurrencesByDay
        .flatMap { (_, occurrences) -> occurrences }
        .groupBy { it.task.id }
        .mapNotNull { (taskId, occurrences) ->
            val row = tasks.first { it.task.id == taskId }
            val counts = occurrences.groupingBy { it.state }.eachCount()
            TaskStat(
                task = row,
                done = counts[OccurrenceState.DONE] ?: 0,
                skipped = counts[OccurrenceState.SKIPPED] ?: 0,
                notDone = (counts[OccurrenceState.MISSED] ?: 0) + (counts[OccurrenceState.PENDING] ?: 0),
            ).takeIf { it.due > 0 }
        }
        .sortedWith(compareBy({ it.rate }, { it.task.task.id }))
    val firstMonth = tasks.filter { it.task.enabled }.minOfOrNull { it.task.startEpochDay }
        ?.let { YearMonth.from(LocalDate.ofEpochDay(it)) }
        ?: YearMonth.from(now.toLocalDate())
    return MonthStats(
        month = month,
        days = occurrencesByDay.map { (date, occurrences) -> summarize(date, occurrences) },
        streak = currentStreak(tasks, index, now),
        perTask = perTask,
        firstMonth = minOf(firstMonth, YearMonth.from(now.toLocalDate())),
    )
}
