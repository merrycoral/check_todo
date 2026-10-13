package com.merrycoral.checktodo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.merrycoral.checktodo.data.Category
import com.merrycoral.checktodo.data.LogStatus
import com.merrycoral.checktodo.data.repeatLabel
import com.merrycoral.checktodo.data.timeLabel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATE_FORMAT = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)

@Composable
fun TodayScreen(
    items: List<TodayItem>,
    categories: List<Category>,
    today: LocalDate,
    onDone: (TodayItem) -> Unit,
    onSkip: (TodayItem) -> Unit,
    onUndo: (TodayItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedCategory by rememberSaveable { mutableStateOf<Long?>(null) }
    val visible = items.filter { selectedCategory == null || it.task.categoryId == selectedCategory }
    val doneCount = visible.count { it.status == LogStatus.DONE }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(today.format(DATE_FORMAT), style = MaterialTheme.typography.headlineSmall)
            Text(
                "$doneCount / ${visible.size} 완료",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            LinearProgressIndicator(
                progress = { if (visible.isEmpty()) 0f else doneCount.toFloat() / visible.size },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { selectedCategory = null },
                        label = { Text("전체") },
                    )
                }
                items(categories, key = { it.id }) { category ->
                    FilterChip(
                        selected = selectedCategory == category.id,
                        onClick = { selectedCategory = category.id },
                        label = { Text(category.name) },
                    )
                }
            }
        }
        if (visible.isEmpty()) {
            item {
                Text(
                    "오늘 할 일이 없어요.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
        }
        items(visible, key = { it.task.id }) { item ->
            TodayRow(item, onDone, onSkip, onUndo)
        }
    }
}

@Composable
private fun TodayRow(
    item: TodayItem,
    onDone: (TodayItem) -> Unit,
    onSkip: (TodayItem) -> Unit,
    onUndo: (TodayItem) -> Unit,
) {
    val done = item.status == LogStatus.DONE
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = done,
                onCheckedChange = { if (done) onUndo(item) else onDone(item) },
            )
            Column(Modifier.weight(1f)) {
                Text(
                    item.task.title,
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                )
                Text(
                    "${item.categoryName} · ${item.task.timeLabel()} · ${item.task.repeatLabel()}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            when (item.status) {
                null -> TextButton(onClick = { onSkip(item) }) { Text("건너뛰기") }
                LogStatus.SKIPPED -> {
                    Text("건너뜀", style = MaterialTheme.typography.labelMedium)
                    TextButton(onClick = { onUndo(item) }) { Text("되돌리기") }
                }
                LogStatus.DONE -> Unit
            }
        }
    }
}
