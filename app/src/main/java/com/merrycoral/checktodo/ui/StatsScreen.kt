package com.merrycoral.checktodo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.merrycoral.checktodo.data.DayOccurrence
import com.merrycoral.checktodo.data.DaySummary
import com.merrycoral.checktodo.data.LogStatus
import com.merrycoral.checktodo.data.MonthStats
import com.merrycoral.checktodo.data.OccurrenceState
import com.merrycoral.checktodo.data.TaskStat
import com.merrycoral.checktodo.data.WEEKDAY_LABELS
import com.merrycoral.checktodo.data.slotLabel
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy년 M월", Locale.KOREAN)
private val DAY_FORMAT = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)

/**
 * 기록 탭: 월 달력과 수행 통계, 선택한 날의 회차 목록(오늘까지는 기록을 고칠 수 있음).
 * 통계는 지금의 할 일 설정을 기준으로 계산한다(사용 중이 아닌 할 일 제외).
 */
@Composable
fun StatsScreen(
    stats: MonthStats?,
    selectedDate: LocalDate,
    occurrences: List<DayOccurrence>,
    today: LocalDate,
    onShowMonth: (YearMonth) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onSetStatus: (DayOccurrence, LogStatus?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (stats == null) return
    val editable = selectedDate <= today

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            MonthHeader(
                month = stats.month,
                canGoBack = stats.month > stats.firstMonth,
                canGoForward = stats.month < YearMonth.from(today),
                onShowMonth = onShowMonth,
            )
        }
        item { SummaryRow(stats) }
        item { CalendarGrid(stats, selectedDate, today, onSelectDate) }
        item { Legend() }
        item {
            Text(
                selectedDate.format(DAY_FORMAT),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (occurrences.isEmpty()) {
            item { Text("이날은 할 일이 없어요.", style = MaterialTheme.typography.bodyMedium) }
        }
        items(occurrences, key = { "${it.task.id}:${it.slot}" }) { occurrence ->
            OccurrenceRow(occurrence, editable, onSetStatus)
        }
        if (stats.perTask.isNotEmpty()) {
            item {
                Text(
                    "할 일별 수행률",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(stats.perTask, key = { it.task.task.id }) { TaskStatRow(it) }
        }
    }
}

@Composable
private fun MonthHeader(month: YearMonth, canGoBack: Boolean, canGoForward: Boolean, onShowMonth: (YearMonth) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onShowMonth(month.minusMonths(1)) }, enabled = canGoBack) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "이전 달")
        }
        Text(
            month.format(MONTH_FORMAT),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { onShowMonth(month.plusMonths(1)) }, enabled = canGoForward) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "다음 달")
        }
    }
}

@Composable
private fun SummaryRow(stats: MonthStats) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SummaryCard(
            label = "수행률",
            value = if (stats.due == 0) "–" else "${(stats.rate * 100).roundToInt()}%",
            caption = "${stats.done} / ${stats.due}회",
            modifier = Modifier.weight(1f),
        )
        SummaryCard(
            label = "연속 달성",
            value = "${stats.streak}일",
            caption = "놓친 회차 없이",
            modifier = Modifier.weight(1f),
        )
        SummaryCard(
            label = "놓침",
            value = "${stats.missed}회",
            caption = "건너뜀 ${stats.skipped}회",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SummaryCard(label: String, value: String, caption: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(caption, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CalendarGrid(stats: MonthStats, selectedDate: LocalDate, today: LocalDate, onSelectDate: (LocalDate) -> Unit) {
    Column {
        Row {
            WEEKDAY_LABELS.forEach {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        // 월요일 시작. 1일 앞의 빈칸 수 = 1일의 요일(월=1) - 1
        val leadingBlanks = stats.month.atDay(1).dayOfWeek.value - 1
        val cells: List<DaySummary?> = List(leadingBlanks) { null } + stats.days
        cells.chunked(7).forEach { week ->
            Row {
                week.forEach { summary ->
                    if (summary == null) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        DayCell(
                            summary = summary,
                            selected = summary.date == selectedDate,
                            isToday = summary.date == today,
                            isFuture = summary.date > today,
                            onClick = { onSelectDate(summary.date) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** 하루 칸의 배경색. 색만으로 구분하지 않도록 칸 안에 '수행/해야 할' 숫자도 함께 표시한다. */
@Composable
private fun dayColors(summary: DaySummary, isFuture: Boolean): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return when {
        isFuture || summary.due == 0 -> Color.Transparent to scheme.onSurface
        summary.isComplete -> scheme.primary to scheme.onPrimary
        summary.missed > 0 && summary.done == 0 -> scheme.errorContainer to scheme.onErrorContainer
        summary.done > 0 -> scheme.primaryContainer to scheme.onPrimaryContainer
        else -> scheme.surfaceVariant to scheme.onSurfaceVariant
    }
}

@Composable
private fun DayCell(
    summary: DaySummary,
    selected: Boolean,
    isToday: Boolean,
    isFuture: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (container, content) = dayColors(summary, isFuture)
    val description = buildString {
        append(summary.date.format(DAY_FORMAT))
        if (!isFuture && summary.due > 0) {
            append(", ${summary.due}회 중 ${summary.done}회 수행")
            if (summary.missed > 0) append(", 놓침 ${summary.missed}회")
        }
    }
    Box(modifier.aspectRatio(1f).padding(2.dp)) {
        Column(
            Modifier
                .fillMaxSize()
                .clip(MaterialTheme.shapes.small)
                .background(container)
                .then(
                    if (selected) {
                        Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, MaterialTheme.shapes.small)
                    } else {
                        Modifier
                    },
                )
                .clickable(onClick = onClick)
                .semantics { contentDescription = description },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                summary.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = content,
                modifier = Modifier.clearAndSetSemantics {},
            )
            if (!isFuture && summary.due > 0) {
                Text(
                    "${summary.done}/${summary.due}",
                    style = MaterialTheme.typography.labelSmall,
                    color = content,
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
        }
    }
}

@Composable
private fun Legend() {
    val scheme = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendItem(scheme.primary, "모두 처리")
        LegendItem(scheme.primaryContainer, "일부 수행")
        LegendItem(scheme.errorContainer, "수행 없이 놓침")
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(MaterialTheme.shapes.extraSmall).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 4.dp))
    }
}

private fun stateLabel(state: OccurrenceState): String = when (state) {
    OccurrenceState.DONE -> "수행"
    OccurrenceState.SKIPPED -> "건너뜀"
    OccurrenceState.PENDING -> "미처리"
    OccurrenceState.MISSED -> "놓침"
    OccurrenceState.UPCOMING -> "예정"
}

@Composable
private fun OccurrenceRow(
    occurrence: DayOccurrence,
    editable: Boolean,
    onSetStatus: (DayOccurrence, LogStatus?) -> Unit,
) {
    val done = occurrence.state == OccurrenceState.DONE
    val categoryColor = Color(occurrence.categoryColor)
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = done,
                onCheckedChange = { onSetStatus(occurrence, if (done) null else LogStatus.DONE) },
                enabled = editable,
                colors = CheckboxDefaults.colors(checkedColor = categoryColor, uncheckedColor = categoryColor),
            )
            Column(Modifier.weight(1f)) {
                Text(occurrence.task.title, style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ColorDot(categoryColor, size = 8.dp)
                    Text(
                        "${occurrence.categoryName} · ${slotLabel(occurrence.slot)}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
            Text(
                stateLabel(occurrence.state),
                style = MaterialTheme.typography.labelMedium,
                color = if (occurrence.state == OccurrenceState.MISSED) MaterialTheme.colorScheme.error else Color.Unspecified,
            )
            if (editable) {
                when (occurrence.state) {
                    OccurrenceState.SKIPPED -> TextButton(onClick = { onSetStatus(occurrence, null) }) { Text("되돌리기") }
                    OccurrenceState.DONE -> Unit
                    else -> TextButton(onClick = { onSetStatus(occurrence, LogStatus.SKIPPED) }) { Text("건너뛰기") }
                }
            }
        }
    }
}

@Composable
private fun TaskStatRow(stat: TaskStat) {
    val categoryColor = Color(stat.task.categoryColor)
    Row(verticalAlignment = Alignment.CenterVertically) {
        ColorDot(categoryColor, size = 12.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(stat.task.task.title, style = MaterialTheme.typography.bodyLarge)
            LinearProgressIndicator(
                progress = { stat.rate },
                color = categoryColor,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
        Text(
            "${stat.done}/${stat.due} · ${(stat.rate * 100).roundToInt()}%",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
