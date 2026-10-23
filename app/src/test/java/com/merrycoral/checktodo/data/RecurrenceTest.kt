package com.merrycoral.checktodo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class RecurrenceTest {
    private fun task(
        repeatType: RepeatType = RepeatType.DAILY,
        times: List<Int> = listOf(8 * 60),
        weekDays: Int = 0,
        monthDay: Int = 1,
    ) = RoutineTask(
        id = 1,
        title = "t",
        categoryId = 1,
        repeatType = repeatType,
        weekDays = weekDays,
        monthDay = monthDay,
        times = times,
    )

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int) = LocalDateTime.of(y, m, d, h, min)

    @Test
    fun `daily with several times picks the nearest later time`() {
        val t = task(times = listOf(8 * 60, 12 * 60 + 30, 20 * 60))

        assertEquals(Occurrence(at(2026, 10, 13, 8, 0), 8 * 60), t.nextOccurrence(at(2026, 10, 13, 7, 0)))
        assertEquals(Occurrence(at(2026, 10, 13, 12, 30), 12 * 60 + 30), t.nextOccurrence(at(2026, 10, 13, 9, 0)))
        assertEquals(Occurrence(at(2026, 10, 13, 20, 0), 20 * 60), t.nextOccurrence(at(2026, 10, 13, 12, 30)))
    }

    @Test
    fun `an occurrence exactly at the reference time is not returned`() {
        val t = task(times = listOf(8 * 60, 20 * 60))

        assertEquals(Occurrence(at(2026, 10, 13, 20, 0), 20 * 60), t.nextOccurrence(at(2026, 10, 13, 8, 0)))
    }

    @Test
    fun `after the last time of the day rolls over to the first time of the next day`() {
        val t = task(times = listOf(8 * 60, 20 * 60))

        assertEquals(Occurrence(at(2026, 10, 14, 8, 0), 8 * 60), t.nextOccurrence(at(2026, 10, 13, 20, 0)))
    }

    @Test
    fun `weekly skips days that are not selected`() {
        // 월(bit0)·수(bit2)
        val t = task(RepeatType.WEEKLY, times = listOf(9 * 60, 21 * 60), weekDays = 0b0000101)

        // 월요일 21:00 이후 → 수요일 첫 시각
        assertEquals(Occurrence(at(2026, 10, 14, 9, 0), 9 * 60), t.nextOccurrence(at(2026, 10, 12, 21, 0)))
        // 화요일은 건너뛴다
        assertEquals(Occurrence(at(2026, 10, 14, 9, 0), 9 * 60), t.nextOccurrence(at(2026, 10, 13, 9, 0)))
    }

    @Test
    fun `monthly day 31 falls back to the last day of shorter months`() {
        val t = task(RepeatType.MONTHLY, times = listOf(9 * 60), monthDay = 31)

        assertEquals(at(2026, 2, 28, 9, 0), t.nextOccurrence(at(2026, 1, 31, 10, 0)).dateTime) // 평년 2월
        assertEquals(at(2028, 2, 29, 9, 0), t.nextOccurrence(at(2028, 1, 31, 10, 0)).dateTime) // 윤년 2월
        assertTrue(t.occursOn(LocalDate.of(2026, 4, 30)))
        assertFalse(t.occursOn(LocalDate.of(2026, 4, 29)))
    }

    @Test
    fun `labels`() {
        assertEquals("08:05", slotLabel(8 * 60 + 5))
        assertEquals("23:59", slotLabel(23 * 60 + 59))
        assertEquals("08:00, 20:30", task(times = listOf(8 * 60, 20 * 60 + 30)).timesLabel())
    }

    @Test
    fun `today items are one per time, sorted, with their own status`() {
        val morningEvening = TaskWithCategory(task(times = listOf(8 * 60, 20 * 60)), "건강", 1)
        val noon = TaskWithCategory(task(times = listOf(12 * 60)).copy(id = 2), "금융", 2)
        val disabled = TaskWithCategory(task(times = listOf(7 * 60)).copy(id = 3, enabled = false), "업무", 3)
        val today = LocalDate.of(2026, 10, 13)
        val logs = listOf(TaskLog(1, today.toEpochDay(), 8 * 60, LogStatus.DONE, 0))

        val items = buildTodayItems(listOf(morningEvening, noon, disabled), logs, today)

        assertEquals(listOf(1L to 480, 2L to 720, 1L to 1200), items.map { it.task.id to it.slot })
        assertEquals(LogStatus.DONE, items[0].status)
        assertNull(items[1].status)
        assertNull(items[2].status) // 같은 할 일의 저녁 회차는 아침 기록의 영향을 받지 않는다
    }
}
