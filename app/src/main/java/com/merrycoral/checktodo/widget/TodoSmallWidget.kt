package com.merrycoral.checktodo.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.CheckboxDefaults
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
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
import androidx.glance.text.TextStyle
import com.merrycoral.checktodo.CheckTodoApp
import com.merrycoral.checktodo.MainActivity
import com.merrycoral.checktodo.data.LogStatus
import com.merrycoral.checktodo.data.TodayItem
import com.merrycoral.checktodo.data.slotLabel
import java.time.LocalDate

private val ProgressColor = ColorProvider(day = Color(0xFF2E7D6B), night = Color(0xFF7FD6C0))
private val ProgressTrackColor = ColorProvider(day = Color(0xFFDDE5E2), night = Color(0xFF3A4442))

/** 2x2 소형 위젯: 오늘 진행률과, 아직 처리하지 않은 회차만 체크박스로 보여준다. 체크하면 목록에서 빠진다. */
class TodoSmallWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = (context.applicationContext as CheckTodoApp).repository
        val today = LocalDate.now()
        val items = repository.loadToday(today)
        provideContent { SmallWidgetContent(today, items) }
    }
}

class TodoSmallWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodoSmallWidget()
}

@Composable
private fun SmallWidgetContent(today: LocalDate, items: List<TodayItem>) {
    val doneCount = items.count { it.status == LogStatus.DONE }
    val remaining = items.filter { it.status == null }
    Column(
        GlanceModifier.fillMaxSize().background(BackgroundColor).cornerRadius(16.dp).padding(10.dp),
    ) {
        Row(
            GlanceModifier.fillMaxWidth().clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "남은 할 일",
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(color = TextColor, fontSize = 14.sp, fontWeight = FontWeight.Bold),
            )
            Text("$doneCount/${items.size}", style = TextStyle(color = MutedColor, fontSize = 12.sp))
        }
        Spacer(GlanceModifier.height(6.dp))
        LinearProgressIndicator(
            progress = if (items.isEmpty()) 0f else doneCount.toFloat() / items.size,
            modifier = GlanceModifier.fillMaxWidth(),
            color = ProgressColor,
            backgroundColor = ProgressTrackColor,
        )
        Spacer(GlanceModifier.height(6.dp))
        when {
            items.isEmpty() -> Text("오늘 할 일이 없어요.", style = TextStyle(color = MutedColor, fontSize = 12.sp))
            remaining.isEmpty() -> Text("오늘 할 일을 모두 처리했어요.", style = TextStyle(color = MutedColor, fontSize = 12.sp))
            else -> LazyColumn {
                items(remaining, itemId = { it.task.id * 1440 + it.slot }) { item -> SmallRow(today, item) }
            }
        }
    }
}

@Composable
private fun SmallRow(today: LocalDate, item: TodayItem) {
    val categoryColor = ColorProvider(day = Color(item.categoryColor), night = Color(item.categoryColor))
    CheckBox(
        checked = false,
        onCheckedChange = actionRunCallback<ToggleTaskAction>(
            actionParametersOf(
                TaskIdKey to item.task.id,
                EpochDayKey to today.toEpochDay(),
                SlotKey to item.slot,
                DoneKey to false,
            ),
        ),
        text = "${slotLabel(item.slot)} ${item.task.title}",
        style = TextStyle(color = TextColor, fontSize = 12.sp),
        colors = CheckboxDefaults.colors(checkedColor = categoryColor, uncheckedColor = categoryColor),
        maxLines = 1,
        modifier = GlanceModifier.fillMaxWidth(),
    )
}
