package com.merrycoral.checktodo.data

import java.time.LocalDate

/** 오늘 해야 할 한 건. [status] 가 null 이면 아직 처리 전. */
data class TodayItem(
    val task: RoutineTask,
    val categoryName: String,
    val categoryColor: Int,
    val status: LogStatus?,
)

/** 앱 화면과 홈 화면 위젯이 함께 쓰는 '오늘 할 일' 계산. [tasks] 의 순서를 유지한다. */
fun buildTodayItems(
    tasks: List<TaskWithCategory>,
    logs: List<TaskLog>,
    today: LocalDate,
): List<TodayItem> {
    val statusByTask = logs.associate { it.taskId to it.status }
    return tasks
        .filter { it.task.enabled && it.task.occursOn(today) }
        .map { TodayItem(it.task, it.categoryName, it.categoryColor, statusByTask[it.task.id]) }
}
