package com.merrycoral.checktodo.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.merrycoral.checktodo.data.RoutineTask
import com.merrycoral.checktodo.data.repeatLabel
import com.merrycoral.checktodo.data.timeLabel

@Composable
fun TaskListScreen(
    rows: List<TaskRow>,
    onEdit: (RoutineTask) -> Unit,
    onToggleEnabled: (RoutineTask, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        // 하단 FAB 에 마지막 항목이 가려지지 않도록 여백을 둔다
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (rows.isEmpty()) {
            item {
                Text("등록된 할 일이 없어요. 아래 버튼으로 추가해 보세요.", style = MaterialTheme.typography.bodyLarge)
            }
        }
        items(rows, key = { it.task.id }) { (task, categoryName) ->
            Card(Modifier.fillMaxWidth().clickable { onEdit(task) }) {
                Row(
                    Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(task.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "$categoryName · ${task.repeatLabel()} · ${task.timeLabel()}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(checked = task.enabled, onCheckedChange = { onToggleEnabled(task, it) })
                }
            }
        }
    }
}
