package com.merrycoral.checktodo.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.CheckboxDefaults
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import com.merrycoral.checktodo.CheckTodoApp
import com.merrycoral.checktodo.MainActivity
import com.merrycoral.checktodo.data.LogStatus
import com.merrycoral.checktodo.data.TodayItem
import com.merrycoral.checktodo.data.slotLabel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATE_FORMAT = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)

private val BackgroundColor = ColorProvider(day = Color.White, night = Color(0xFF1F1F1F))
private val TextColor = ColorProvider(day = Color(0xFF1C1B1F), night = Color(0xFFE6E1E5))
private val MutedColor = ColorProvider(day = Color(0xFF6B6B6B), night = Color(0xFFA0A0A0))

val TaskIdKey = ActionParameters.Key<Long>("task_id")
val EpochDayKey = ActionParameters.Key<Long>("epoch_day")
val SlotKey = ActionParameters.Key<Int>("slot")
val DoneKey = ActionParameters.Key<Boolean>("done")

/** 오늘 할 일을 보여주고, 체크박스로 수행/미수행을 토글하는 홈 화면 위젯. */
class TodoWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = (context.applicationContext as CheckTodoApp).repository
        val today = LocalDate.now()
        val items = repository.loadToday(today)
        provideContent { WidgetContent(today, items) }
    }
}

class TodoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodoWidget()
}

/** 위젯 체크박스를 눌렀을 때: 수행 ↔ 미수행 토글. 위젯은 위젯이 그려질 당시의 날짜([EpochDayKey]) 기준으로 기록한다. */
class ToggleTaskAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val taskId = checkNotNull(parameters[TaskIdKey])
        val epochDay = checkNotNull(parameters[EpochDayKey])
        val slot = checkNotNull(parameters[SlotKey])
        val wasDone = checkNotNull(parameters[DoneKey])
        val repository = (context.applicationContext as CheckTodoApp).repository
        if (wasDone) {
            repository.clearLog(taskId, epochDay, slot)
        } else {
            repository.setLog(taskId, epochDay, slot, LogStatus.DONE)
        }
    }
}

@Composable
private fun WidgetContent(today: LocalDate, items: List<TodayItem>) {
    val doneCount = items.count { it.status == LogStatus.DONE }
    Column(
        GlanceModifier.fillMaxSize().background(BackgroundColor).cornerRadius(16.dp).padding(12.dp),
    ) {
        Row(
            GlanceModifier.fillMaxWidth().clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                today.format(DATE_FORMAT),
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(color = TextColor, fontSize = 16.sp, fontWeight = FontWeight.Bold),
            )
            Text("$doneCount / ${items.size}", style = TextStyle(color = MutedColor, fontSize = 13.sp))
        }
        Spacer(GlanceModifier.height(8.dp))
        if (items.isEmpty()) {
            Text("오늘 할 일이 없어요.", style = TextStyle(color = MutedColor, fontSize = 14.sp))
        } else {
            LazyColumn {
                items(items, itemId = { it.task.id * 1440 + it.slot }) { item -> TodoRow(today, item) }
            }
        }
    }
}

@Composable
private fun TodoRow(today: LocalDate, item: TodayItem) {
    val done = item.status == LogStatus.DONE
    val categoryColor = ColorProvider(day = Color(item.categoryColor), night = Color(item.categoryColor))
    val label = buildString {
        append(slotLabel(item.slot)).append("  ").append(item.task.title)
        if (item.status == LogStatus.SKIPPED) append("  (건너뜀)")
    }
    CheckBox(
        checked = done,
        onCheckedChange = actionRunCallback<ToggleTaskAction>(
            actionParametersOf(
                TaskIdKey to item.task.id,
                EpochDayKey to today.toEpochDay(),
                SlotKey to item.slot,
                DoneKey to done,
            ),
        ),
        text = label,
        style = TextStyle(
            color = if (done || item.status == LogStatus.SKIPPED) MutedColor else TextColor,
            fontSize = 14.sp,
            textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None,
        ),
        colors = CheckboxDefaults.colors(checkedColor = categoryColor, uncheckedColor = categoryColor),
        modifier = GlanceModifier.fillMaxWidth(),
    )
}
