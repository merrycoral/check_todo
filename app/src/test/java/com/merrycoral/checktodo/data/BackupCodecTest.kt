package com.merrycoral.checktodo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupCodecTest {
    private val snapshot = BackupSnapshot(
        categories = listOf(Category(1, "건강", 0xFF43A047.toInt()), Category(2, "내 카테고리", 0xFF607D8B.toInt())),
        tasks = listOf(
            RoutineTask(
                id = 10,
                title = "영양제 \"비타민\"",
                categoryId = 1,
                repeatType = RepeatType.WEEKLY,
                weekDays = 0b0010101,
                monthDay = 1,
                times = listOf(8 * 60, 20 * 60 + 30),
                memo = "비타민D 1정\n식후",
                enabled = true,
            ),
        ),
        logs = listOf(
            TaskLog(10, 20000, 8 * 60, LogStatus.DONE, 111),
            TaskLog(10, 20000, 20 * 60 + 30, LogStatus.SKIPPED, 222),
        ),
    )

    @Test
    fun `round trip keeps times, memo, colors and per time logs`() {
        val decoded = BackupCodec.decode(BackupCodec.encode(snapshot, exportedAt = 1))

        assertEquals(snapshot, decoded)
    }

    @Test
    fun `version 1 backups import as single time tasks`() {
        val v1 = """
            {"app":"check_todo","formatVersion":1,"exportedAt":1,
             "categories":[{"id":1,"name":"건강","color":-12345}],
             "tasks":[{"id":5,"title":"약","categoryId":1,"repeatType":"DAILY","weekDays":0,"monthDay":1,
                       "hour":7,"minute":45,"enabled":true}],
             "logs":[{"taskId":5,"epochDay":20000,"status":"DONE","loggedAt":9}]}
        """.trimIndent()

        val decoded = BackupCodec.decode(v1)

        assertEquals(listOf(7 * 60 + 45), decoded.tasks.single().times)
        assertEquals("", decoded.tasks.single().memo)
        assertEquals(7 * 60 + 45, decoded.logs.single().slot)
    }

    @Test
    fun `rejects files from other apps and unknown versions`() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.decode("""{"app":"other","formatVersion":2}""")
        }
        assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.decode("""{"app":"check_todo","formatVersion":99}""")
        }
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode("not json") }
    }

    @Test
    fun `rejects invalid times`() {
        fun withTimes(times: List<Int>) =
            BackupCodec.encode(snapshot.copy(tasks = listOf(snapshot.tasks[0].copy(times = times))), 1)

        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(withTimes(emptyList())) }
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(withTimes(listOf(480, 480))) }
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(withTimes(listOf(1200, 480))) }
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(withTimes(listOf(1440))) }
    }

    @Test
    fun `rejects logs that point to a missing task`() {
        val broken = snapshot.copy(logs = listOf(TaskLog(999, 20000, 480, LogStatus.DONE, 1)))

        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(BackupCodec.encode(broken, 1)) }
    }
}
