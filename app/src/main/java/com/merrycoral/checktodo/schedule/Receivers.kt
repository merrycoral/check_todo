package com.merrycoral.checktodo.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.merrycoral.checktodo.CheckTodoApp
import com.merrycoral.checktodo.data.LogStatus
import kotlinx.coroutines.launch

/** 비동기 작업이 끝날 때까지 receiver 를 살려두고 [block] 을 앱 스코프에서 실행한다. */
private fun BroadcastReceiver.runAsync(context: Context, block: suspend (CheckTodoApp) -> Unit) {
    val app = context.applicationContext as CheckTodoApp
    val pending = goAsync()
    app.appScope.launch {
        try {
            block(app)
        } finally {
            pending.finish()
        }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1)
        val epochDay = intent.getLongExtra(EXTRA_EPOCH_DAY, -1)
        val slot = intent.getIntExtra(EXTRA_SLOT, -1)
        check(taskId > 0 && epochDay >= 0 && slot >= 0) { "알람 intent 에 taskId/epochDay/slot 이 없음" }
        val snooze = intent.getBooleanExtra(EXTRA_SNOOZE, false)
        runAsync(context) { it.repository.onAlarmFired(taskId, epochDay, slot, snooze) }
    }
}

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1)
        val epochDay = intent.getLongExtra(EXTRA_EPOCH_DAY, -1)
        val slot = intent.getIntExtra(EXTRA_SLOT, -1)
        check(taskId > 0 && epochDay >= 0 && slot >= 0) { "액션 intent 에 taskId/epochDay/slot 이 없음" }
        val action = intent.action
        runAsync(context) { app ->
            when (action) {
                ACTION_DONE -> app.repository.setLog(taskId, epochDay, slot, LogStatus.DONE)
                ACTION_SKIP -> app.repository.setLog(taskId, epochDay, slot, LogStatus.SKIPPED)
                ACTION_SNOOZE -> app.repository.snooze(taskId, epochDay, slot)
                else -> error("알 수 없는 액션: $action")
            }
        }
    }
}

class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        runAsync(context) { it.repository.rescheduleAll() }
    }
}
