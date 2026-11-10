package com.merrycoral.checktodo.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** 백업 대상 전체 데이터. id 를 그대로 보존해 관계(카테고리-할 일-기록)를 유지한다. */
data class BackupSnapshot(
    val categories: List<Category>,
    val tasks: List<RoutineTask>,
    val logs: List<TaskLog>,
)

/**
 * 백업 파일(JSON) 인코더/디코더. 디코딩은 파일 전체를 검증하며, 이상이 있으면 예외로 알린다.
 *
 * 형식 버전: 3 = 2 + 할 일의 시작일(`startEpochDay`).
 * 2 = 알림 시각 여러 개(`times`)·메모(`memo`)·기록의 회차 시각(`slot`) — 읽을 수만 있다.
 * 1 = 할 일당 알림 시각 하나(`hour`/`minute`), 메모와 회차 시각 없음 — 읽을 수만 있다.
 * 시작일이 없는 1·2 형식은 DB 마이그레이션과 같은 규칙(가장 이른 기록일, 없으면 복원한 날)으로 채운다.
 */
object BackupCodec {
    private const val APP_ID = "check_todo"
    private const val FORMAT_VERSION = 3

    fun encode(snapshot: BackupSnapshot, exportedAt: Long): String = JSONObject()
        .put("app", APP_ID)
        .put("formatVersion", FORMAT_VERSION)
        .put("exportedAt", exportedAt)
        .put("categories", JSONArray().also { array ->
            snapshot.categories.forEach {
                array.put(JSONObject().put("id", it.id).put("name", it.name).put("color", it.color))
            }
        })
        .put("tasks", JSONArray().also { array ->
            snapshot.tasks.forEach {
                array.put(
                    JSONObject()
                        .put("id", it.id)
                        .put("title", it.title)
                        .put("categoryId", it.categoryId)
                        .put("repeatType", it.repeatType.name)
                        .put("weekDays", it.weekDays)
                        .put("monthDay", it.monthDay)
                        .put("times", JSONArray(it.times))
                        .put("startEpochDay", it.startEpochDay)
                        .put("memo", it.memo)
                        .put("enabled", it.enabled),
                )
            }
        })
        .put("logs", JSONArray().also { array ->
            snapshot.logs.forEach {
                array.put(
                    JSONObject()
                        .put("taskId", it.taskId)
                        .put("epochDay", it.epochDay)
                        .put("slot", it.slot)
                        .put("status", it.status.name)
                        .put("loggedAt", it.loggedAt),
                )
            }
        })
        .toString(2)

    /**
     * @param today 시작일이 없는 옛 형식에서 기록이 없는 할 일의 시작일로 쓴다
     * @throws IllegalArgumentException 형식이 맞지 않거나 데이터가 유효하지 않을 때 (JSON 파싱 오류 포함)
     */
    fun decode(text: String, today: LocalDate = LocalDate.now()): BackupSnapshot {
        val root = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw IllegalArgumentException("백업 파일을 읽을 수 없어요. (JSON 형식이 아님)", e)
        }
        require(root.optString("app") == APP_ID) { "이 앱의 백업 파일이 아니에요." }
        val version = root.optInt("formatVersion", -1)
        require(version in 1..FORMAT_VERSION) { "지원하지 않는 백업 버전이에요: $version" }

        val categories = root.getJSONArray("categories").objects().map {
            Category(id = it.getLong("id"), name = it.getString("name"), color = it.getInt("color"))
        }
        // 옛 형식(1·2)의 시작일을 정하기 위한 할 일별 가장 이른 기록일
        val earliestLogDay = root.getJSONArray("logs").objects()
            .groupBy({ it.getLong("taskId") }, { it.getLong("epochDay") })
            .mapValues { (_, days) -> days.min() }
        val tasks = root.getJSONArray("tasks").objects().map {
            val id = it.getLong("id")
            RoutineTask(
                id = id,
                title = it.getString("title"),
                categoryId = it.getLong("categoryId"),
                repeatType = enumValue<RepeatType>(it.getString("repeatType")),
                weekDays = it.getInt("weekDays"),
                monthDay = it.getInt("monthDay"),
                times = if (version == 1) listOf(legacyTime(it)) else it.getJSONArray("times").ints(),
                startEpochDay = if (version >= 3) {
                    it.getLong("startEpochDay")
                } else {
                    minOf(earliestLogDay[id] ?: today.toEpochDay(), today.toEpochDay())
                },
                memo = if (version == 1) "" else it.getString("memo"),
                enabled = it.getBoolean("enabled"),
            )
        }
        val firstTimeByTask = tasks.associate { it.id to it.times.firstOrNull() }
        val logs = root.getJSONArray("logs").objects().map {
            val taskId = it.getLong("taskId")
            TaskLog(
                taskId = taskId,
                epochDay = it.getLong("epochDay"),
                // v1 은 할 일당 시각이 하나였으므로 기록의 회차 시각은 그 시각이다
                slot = if (version == 1) {
                    requireNotNull(firstTimeByTask[taskId]) { "수행 기록이 존재하지 않는 할 일을 가리켜요." }
                } else {
                    it.getInt("slot")
                },
                status = enumValue<LogStatus>(it.getString("status")),
                loggedAt = it.getLong("loggedAt"),
            )
        }
        validate(categories, tasks, logs)
        return BackupSnapshot(categories, tasks, logs)
    }

    private fun legacyTime(task: JSONObject): Int {
        val hour = task.getInt("hour")
        val minute = task.getInt("minute")
        require(hour in 0..23 && minute in 0..59) { "'${task.getString("title")}' 의 알림 시각이 올바르지 않아요." }
        return hour * 60 + minute
    }

    private fun validate(categories: List<Category>, tasks: List<RoutineTask>, logs: List<TaskLog>) {
        require(categories.map { it.id }.toSet().size == categories.size) { "카테고리 id 가 중복돼요." }
        require(categories.map { it.name }.toSet().size == categories.size) { "카테고리 이름이 중복돼요." }
        require(tasks.map { it.id }.toSet().size == tasks.size) { "할 일 id 가 중복돼요." }
        require(logs.map { Triple(it.taskId, it.epochDay, it.slot) }.toSet().size == logs.size) { "수행 기록이 중복돼요." }

        val categoryIds = categories.map { it.id }.toSet()
        val taskIds = tasks.map { it.id }.toSet()
        tasks.forEach {
            require(it.categoryId in categoryIds) { "'${it.title}' 의 카테고리가 백업에 없어요." }
            require(it.times.isNotEmpty() && it.times.all { slot -> slot in 0..1439 } && it.times == it.times.distinct().sorted()) {
                "'${it.title}' 의 알림 시각이 올바르지 않아요."
            }
            require(it.monthDay in 1..31) { "'${it.title}' 의 매월 일자가 올바르지 않아요." }
            require(it.weekDays in 0..0b1111111) { "'${it.title}' 의 요일 값이 올바르지 않아요." }
            require(it.repeatType != RepeatType.WEEKLY || it.weekDays != 0) { "'${it.title}' 의 반복 요일이 비어 있어요." }
        }
        logs.forEach {
            require(it.taskId in taskIds) { "수행 기록이 존재하지 않는 할 일을 가리켜요." }
            require(it.slot in 0..1439) { "수행 기록의 시각이 올바르지 않아요." }
        }
    }

    private fun JSONArray.objects(): List<JSONObject> = List(length()) { getJSONObject(it) }

    private fun JSONArray.ints(): List<Int> = List(length()) { getInt(it) }

    private inline fun <reified E : Enum<E>> enumValue(name: String): E =
        enumValues<E>().firstOrNull { it.name == name }
            ?: throw IllegalArgumentException("알 수 없는 값: $name")
}
