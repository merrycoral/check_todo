package com.merrycoral.checktodo.data

import java.time.LocalDate

/** 오늘 해야 할 한 회차(할 일의 알림 시각 하나). [status] 가 null 이면 아직 처리 전. */
data class TodayItem(
    val task: RoutineTask,
    val slot: Int,
    val categoryName: String,
    val categoryColor: Int,
    val status: LogStatus?,
) {
    /** 목록에서 회차를 구분하는 키 */
    val key: String get() = "${task.id}:$slot"
}

/** 앱 화면과 홈 화면 위젯이 함께 쓰는 '오늘 할 일' 계산. 시각 → 할 일 순으로 정렬한다. */
fun buildTodayItems(
    tasks: List<TaskWithCategory>,
    logs: List<TaskLog>,
    today: LocalDate,
): List<TodayItem> {
    val statusByOccurrence = logs.associate { (it.taskId to it.slot) to it.status }
    return tasks
        .filter { it.task.enabled && it.task.occursOn(today) }
        .flatMap { row ->
            row.task.times.map { slot ->
                TodayItem(row.task, slot, row.categoryName, row.categoryColor, statusByOccurrence[row.task.id to slot])
            }
        }
        .sortedWith(compareBy({ it.slot }, { it.task.id }))
}
