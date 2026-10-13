package com.merrycoral.checktodo.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun AppRoot(viewModel: MainViewModel) {
    val context = LocalContext.current
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val taskRows by viewModel.taskRows.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val todayItems by viewModel.todayItems.collectAsStateWithLifecycle()
    val today by viewModel.today.collectAsStateWithLifecycle()
    val exactAlarmAllowed by viewModel.exactAlarmAllowed.collectAsStateWithLifecycle()
    val notificationsAllowed by viewModel.notificationsAllowed.collectAsStateWithLifecycle()
    val backupMessage by viewModel.backupMessage.collectAsStateWithLifecycle()
    val pendingRestore by viewModel.pendingRestore.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var categoryColorsOpen by rememberSaveable { mutableStateOf(false) }
    // 0 = 새 할 일
    var editingTaskId by rememberSaveable { mutableLongStateOf(0L) }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.onResume()
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.exportBackup(uri)
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.prepareRestore(uri)
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    BackHandler(enabled = editorOpen) { editorOpen = false }

    if (editorOpen) {
        TaskEditScreen(
            task = tasks.firstOrNull { it.id == editingTaskId },
            categories = categories,
            onSave = {
                viewModel.saveTask(it)
                editorOpen = false
            },
            onDelete = {
                viewModel.deleteTask(it)
                editorOpen = false
            },
            onAddCategory = viewModel::addCategory,
            onBack = { editorOpen = false },
        )
        return
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Default.Check, contentDescription = null) },
                    label = { Text("오늘") },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    label = { Text("할 일 관리") },
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("설정") },
                )
            }
        },
        floatingActionButton = {
            if (tab == 1) {
                ExtendedFloatingActionButton(
                    onClick = {
                        editingTaskId = 0L
                        editorOpen = true
                    },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("할 일 추가") },
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            if (!notificationsAllowed) {
                PermissionBanner(
                    message = "알림이 꺼져 있어 할 일 알림을 받을 수 없어요.",
                    onOpenSettings = {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                        )
                    },
                )
            }
            if (!exactAlarmAllowed) {
                PermissionBanner(
                    message = "정해진 시각에 알림을 받으려면 '알람 및 리마인더' 권한이 필요해요.",
                    onOpenSettings = {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")),
                        )
                    },
                )
            }
            when (tab) {
                0 -> TodayScreen(
                    items = todayItems,
                    categories = categories,
                    today = today,
                    onDone = viewModel::setDone,
                    onSkip = viewModel::skip,
                    onUndo = viewModel::undo,
                )
                2 -> SettingsScreen(
                    backupMessage = backupMessage,
                    onExport = { exportLauncher.launch("check_todo_backup_${LocalDate.now()}.json") },
                    onRestore = { restoreLauncher.launch(arrayOf("*/*")) },
                )
                else -> TaskListScreen(
                    rows = taskRows,
                    onEdit = {
                        editingTaskId = it.id
                        editorOpen = true
                    },
                    onToggleEnabled = viewModel::setEnabled,
                    onManageCategories = { categoryColorsOpen = true },
                )
            }
        }
    }

    pendingRestore?.let { snapshot ->
        RestoreConfirmDialog(
            snapshot = snapshot,
            onConfirm = viewModel::confirmRestore,
            onDismiss = viewModel::cancelRestore,
        )
    }
    if (categoryColorsOpen) {
        CategoryColorDialog(
            categories = categories,
            onColorChange = viewModel::setCategoryColor,
            onDismiss = { categoryColorsOpen = false },
        )
    }
}

@Composable
private fun PermissionBanner(message: String, onOpenSettings: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(PaddingValues(horizontal = 16.dp, vertical = 8.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onOpenSettings) { Text("설정") }
        }
    }
}
