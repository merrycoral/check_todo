package com.merrycoral.checktodo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.merrycoral.checktodo.data.Category
import com.merrycoral.checktodo.data.CategoryPalette
import com.merrycoral.checktodo.data.RepeatType
import com.merrycoral.checktodo.data.RoutineTask
import com.merrycoral.checktodo.data.WEEKDAY_LABELS

private val REPEAT_LABELS = mapOf(
    RepeatType.DAILY to "매일",
    RepeatType.WEEKLY to "매주",
    RepeatType.MONTHLY to "매월",
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskEditScreen(
    task: RoutineTask?,
    categories: List<Category>,
    onSave: (RoutineTask) -> Unit,
    onDelete: (RoutineTask) -> Unit,
    onAddCategory: (name: String, color: Int, onAdded: (Long) -> Unit) -> Unit,
    onBack: () -> Unit,
) {
    var title by remember { mutableStateOf(task?.title.orEmpty()) }
    // 사용자가 고른 카테고리. 고르지 않았다면 기존 할 일의 카테고리, 새 할 일이면 목록의 첫 카테고리를 쓴다.
    var chosenCategoryId by remember { mutableStateOf(task?.categoryId) }
    val categoryId = chosenCategoryId ?: categories.firstOrNull()?.id
    var repeatType by remember { mutableStateOf(task?.repeatType ?: RepeatType.DAILY) }
    var weekDays by remember { mutableIntStateOf(task?.weekDays ?: 0) }
    var monthDay by remember { mutableIntStateOf(task?.monthDay ?: 1) }
    var hour by remember { mutableIntStateOf(task?.hour ?: 9) }
    var minute by remember { mutableIntStateOf(task?.minute ?: 0) }

    var showTimePicker by remember { mutableStateOf(false) }
    var showCategoryDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val canSave = title.isNotBlank() && categoryId != null &&
        (repeatType != RepeatType.WEEKLY || weekDays != 0)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (task == null) "할 일 추가" else "할 일 수정") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    if (task != null) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "삭제")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("할 일") },
                placeholder = { Text("예: 영양제 먹기") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Section("카테고리") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.forEach { category ->
                        FilterChip(
                            selected = categoryId == category.id,
                            onClick = { chosenCategoryId = category.id },
                            label = { Text(category.name) },
                            leadingIcon = { ColorDot(Color(category.color)) },
                        )
                    }
                    FilterChip(
                        selected = false,
                        onClick = { showCategoryDialog = true },
                        label = { Text("+ 추가") },
                    )
                }
            }

            Section("반복") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    RepeatType.entries.forEachIndexed { index, type ->
                        SegmentedButton(
                            selected = repeatType == type,
                            onClick = { repeatType = type },
                            shape = SegmentedButtonDefaults.itemShape(index, RepeatType.entries.size),
                        ) { Text(REPEAT_LABELS.getValue(type)) }
                    }
                }
                when (repeatType) {
                    RepeatType.DAILY -> Unit
                    RepeatType.WEEKLY -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WEEKDAY_LABELS.forEachIndexed { index, label ->
                            val bit = 1 shl index
                            FilterChip(
                                selected = weekDays and bit != 0,
                                onClick = { weekDays = weekDays xor bit },
                                label = { Text(label) },
                            )
                        }
                    }
                    RepeatType.MONTHLY -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(onClick = { monthDay = if (monthDay <= 1) 31 else monthDay - 1 }) { Text("−") }
                            Text(
                                "매월 ${monthDay}일",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                            OutlinedButton(onClick = { monthDay = if (monthDay >= 31) 1 else monthDay + 1 }) { Text("+") }
                        }
                        if (monthDay > 28) {
                            Text(
                                "${monthDay}일이 없는 달에는 말일에 알려줘요.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            Section("알림 시각") {
                OutlinedButton(onClick = { showTimePicker = true }) {
                    Text("%02d:%02d".format(hour, minute))
                }
            }

            Button(
                onClick = {
                    onSave(
                        RoutineTask(
                            id = task?.id ?: 0,
                            title = title.trim(),
                            categoryId = requireNotNull(categoryId),
                            repeatType = repeatType,
                            weekDays = if (repeatType == RepeatType.WEEKLY) weekDays else 0,
                            monthDay = monthDay,
                            hour = hour,
                            minute = minute,
                            enabled = task?.enabled ?: true,
                        ),
                    )
                },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("저장") }
            if (repeatType == RepeatType.WEEKLY && weekDays == 0) {
                Text("요일을 하나 이상 선택해 주세요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showTimePicker) {
        TimePickerDialog(
            initialHour = hour,
            initialMinute = minute,
            onConfirm = { h, m ->
                hour = h
                minute = m
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false },
        )
    }
    if (showCategoryDialog) {
        AddCategoryDialog(
            existingNames = categories.map { it.name },
            onConfirm = { name, color ->
                showCategoryDialog = false
                onAddCategory(name, color) { chosenCategoryId = it }
            },
            onDismiss = { showCategoryDialog = false },
        )
    }
    if (showDeleteDialog && task != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("할 일 삭제") },
            text = { Text("'${task.title}'과(와) 수행 기록이 모두 삭제돼요.") },
            confirmButton = { TextButton(onClick = { onDelete(task) }) { Text("삭제") } },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("취소") } },
        )
    }
}

@Composable
private fun Section(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(initialHour, initialMinute, is24Hour = true)
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                TimePicker(state = state)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("취소") }
                    TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text("확인") }
                }
            }
        }
    }
}

@Composable
private fun AddCategoryDialog(
    existingNames: List<String>,
    onConfirm: (name: String, color: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var color by remember { mutableIntStateOf(CategoryPalette.DEFAULT_COLOR) }
    val trimmed = name.trim()
    val duplicate = existingNames.any { it.equals(trimmed, ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("카테고리 추가") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("카테고리 이름") },
                    singleLine = true,
                    isError = duplicate,
                    supportingText = if (duplicate) ({ Text("이미 있는 카테고리예요.") }) else null,
                )
                Text("색상", style = MaterialTheme.typography.titleSmall)
                ColorPalette(selected = color, onSelect = { color = it })
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(trimmed, color) }, enabled = trimmed.isNotEmpty() && !duplicate) { Text("추가") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
