package com.merrycoral.checktodo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

class StatsTest {
    private val start = LocalDate.of(2026, 11, 1)

    private fun row(
        id: Long,
        times: List<Int> = listOf(8 * 60),
        repeatType: RepeatType = RepeatType.DAILY,
        weekDays: Int = 0,
        startDate: LocalDate = start,
        enabled: Boolean = true,
    ) = TaskWithCategory(
        RoutineTask(
            id = id,
            title = "t$id",
            categoryId = 1,
            repeatType = repeatType,
            weekDays = weekDays,
            times = times,
            startEpochDay = startDate.toEpochDay(),
            enabled = enabled,
        ),
        "건강",
        1,
    )

    private fun log(taskId: Long, date: LocalDate, slot: Int, status: LogStatus) =
        TaskLog(taskId, date.toEpochDay(), slot, status, 0)

    private fun states(date: LocalDate, tasks: List<TaskWithCategory>, logs: List<TaskLog>, now: LocalDateTime) =
        occurrencesOn(date, tasks, LogIndex(logs), now).map { it.state }

    @Test
    fun `occurrence states depend on the record and the time`() {
        val tasks = listOf(row(1, times = listOf(8 * 60, 20 * 60)))
        val now = LocalDateTime.of(2026, 11, 10, 12, 0)
        val today = now.toLocalDate()
        val yesterday = today.minusDays(1)

        // 지난 날 기록 없음 → 놓침
        assertEquals(listOf(OccurrenceState.MISSED, OccurrenceState.MISSED), states(yesterday, tasks, emptyList(), now))
        // 오늘: 시각이 지난 회차는 미처리, 아직이면 예정
        assertEquals(listOf(OccurrenceState.PENDING, OccurrenceState.UPCOMING), states(today, tasks, emptyList(), now))
        // 기록이 있으면 시각과 상관없이 기록대로 (저녁 회차를 미리 수행)
        val logs = listOf(log(1, today, 20 * 60, LogStatus.DONE), log(1, today, 8 * 60, LogStatus.SKIPPED))
        assertEquals(listOf(OccurrenceState.SKIPPED, OccurrenceState.DONE), states(today, tasks, logs, now))
        // 미래 날짜는 예정
        assertEquals(listOf(OccurrenceState.UPCOMING, OccurrenceState.UPCOMING), states(today.plusDays(1), tasks, emptyList(), now))
        // 회차는 자기 날짜를 가진다 (기록 화면에서 이 날짜로 저장)
        assertTrue(occurrencesOn(yesterday, tasks, LogIndex(emptyList()), now).all { it.date == yesterday })
    }

    @Test
    fun `days before the start date and disabled tasks are not counted`() {
        val tasks = listOf(
            row(1, startDate = LocalDate.of(2026, 11, 5)),
            row(2, enabled = false),
        )
        val now = LocalDateTime.of(2026, 11, 10, 12, 0)

        assertTrue(states(LocalDate.of(2026, 11, 4), tasks, emptyList(), now).isEmpty())
        assertEquals(listOf(OccurrenceState.MISSED), states(LocalDate.of(2026, 11, 5), tasks, emptyList(), now))
    }

    @Test
    fun `a day is complete when nothing was missed, skipping counts as handled`() {
        val date = LocalDate.of(2026, 11, 3)
        val tasks = listOf(row(1, times = listOf(8 * 60, 20 * 60)))
        val now = LocalDateTime.of(2026, 11, 10, 12, 0)
        val index = LogIndex(listOf(log(1, date, 8 * 60, LogStatus.DONE), log(1, date, 20 * 60, LogStatus.SKIPPED)))

        val summary = summarize(date, occurrencesOn(date, tasks, index, now))

        assertEquals(2, summary.due)
        assertEquals(1, summary.done)
        assertTrue(summary.isComplete)
    }

    @Test
    fun `streak counts back from yesterday while today is in progress and stops at a missed day`() {
        val tasks = listOf(row(1, times = listOf(8 * 60, 20 * 60)))
        val now = LocalDateTime.of(2026, 11, 10, 12, 0)
        val logs = (6..9).flatMap { day ->
            val date = LocalDate.of(2026, 11, day)
            listOf(log(1, date, 8 * 60, LogStatus.DONE), log(1, date, 20 * 60, LogStatus.DONE))
        } + log(1, LocalDate.of(2026, 11, 10), 8 * 60, LogStatus.DONE) // 오늘은 저녁 회차가 남음

        // 11/6~11/9 4일 완료, 11/5 는 놓침
        assertEquals(4, currentStreak(tasks, LogIndex(logs), now))
    }

    @Test
    fun `days with nothing due neither break nor extend the streak`() {
        // 월(bit0)·수(bit2)만
        val tasks = listOf(row(1, repeatType = RepeatType.WEEKLY, weekDays = 0b0000101, startDate = LocalDate.of(2026, 11, 2)))
        // 2026-11-10 은 화요일. 11/9(월)·11/4(수)·11/2(월) 완료
        val now = LocalDateTime.of(2026, 11, 10, 12, 0)
        val logs = listOf(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 9))
            .map { log(1, it, 8 * 60, LogStatus.DONE) }

        // 오늘(화)은 할 일이 없어 세지 않고, 11/9(월)·11/4(수)·11/2(월) 완료. 그 사이 날들은 할 일이 없어 건너뜀 → 3
        assertEquals(3, currentStreak(tasks, LogIndex(logs), now))
    }

    @Test
    fun `today counts toward the streak once everything is handled`() {
        val tasks = listOf(row(1, startDate = LocalDate.of(2026, 11, 10)))
        val now = LocalDateTime.of(2026, 11, 10, 12, 0)

        assertEquals(0, currentStreak(tasks, LogIndex(emptyList()), now))
        assertEquals(1, currentStreak(tasks, LogIndex(listOf(log(1, now.toLocalDate(), 8 * 60, LogStatus.DONE))), now))
    }

    @Test
    fun `month stats add up the days and rank tasks by completion`() {
        val tasks = listOf(row(1), row(2))
        val now = LocalDateTime.of(2026, 11, 3, 23, 0)
        val logs = listOf(
            log(1, LocalDate.of(2026, 11, 1), 480, LogStatus.DONE),
            log(1, LocalDate.of(2026, 11, 2), 480, LogStatus.DONE),
            log(1, LocalDate.of(2026, 11, 3), 480, LogStatus.DONE),
            log(2, LocalDate.of(2026, 11, 1), 480, LogStatus.SKIPPED),
        )

        val stats = buildMonthStats(YearMonth.of(2026, 11), tasks, logs, now)

        assertEquals(30, stats.days.size)
        assertEquals(6, stats.due) // 11/1~11/3, 할 일 2개
        assertEquals(3, stats.done)
        assertEquals(1, stats.skipped)
        // 11/3 의 할 일 2 는 오늘 회차라 '놓침'이 아니라 '미처리'
        assertEquals(1, stats.missed)
        assertEquals(listOf(2L, 1L), stats.perTask.map { it.task.task.id }) // 수행률 낮은 순
        assertEquals(YearMonth.of(2026, 11), stats.firstMonth)
        assertTrue(stats.days[0].isComplete) // 11/1: 수행 + 건너뜀 → 놓친 회차 없음
        assertFalse(stats.days[1].isComplete) // 11/2: 할 일 2 놓침
    }
}
