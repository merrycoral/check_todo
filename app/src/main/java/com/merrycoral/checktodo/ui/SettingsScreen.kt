package com.merrycoral.checktodo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.merrycoral.checktodo.data.BackupSnapshot

@Composable
fun SettingsScreen(
    backupMessage: String?,
    onExport: () -> Unit,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("백업 / 복원", style = MaterialTheme.typography.titleLarge)
        Text(
            "데이터는 이 기기에만 저장돼요. 기기를 바꾸거나 앱을 지우기 전에 백업 파일을 만들어 Google Drive 등 원하는 곳에 보관하세요.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "백업 항목: 할 일(반복 주기·알림 시각·사용 여부), 카테고리(직접 추가한 카테고리와 카테고리별 색상 포함), 수행/건너뛰기 기록",
            style = MaterialTheme.typography.bodySmall,
        )
        Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) { Text("백업 파일 내보내기") }
        OutlinedButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) { Text("백업 파일에서 복원") }
        if (backupMessage != null) {
            Text(backupMessage, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** 복원 전 확인: 현재 데이터가 모두 백업 내용으로 교체된다는 점을 알린다. */
@Composable
fun RestoreConfirmDialog(snapshot: BackupSnapshot, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("백업에서 복원할까요?") },
        text = {
            Text(
                "현재 데이터가 모두 지워지고 백업 파일의 내용으로 바뀌어요. 카테고리 이름과 색상도 백업 시점의 상태로 돌아가요.\n\n" +
                    "카테고리 ${snapshot.categories.size}개 · 할 일 ${snapshot.tasks.size}개 · 수행 기록 ${snapshot.logs.size}건",
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("복원") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
