package com.merrycoral.checktodo.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

const val EXTRA_TASK_ID = "task_id"
const val EXTRA_EPOCH_DAY = "epoch_day"
const val EXTRA_SNOOZE = "snooze"

/**
 * AlarmManager 래퍼. 무엇을 언제 울릴지는 Repository 가 정하고(pending_alarms 에 기록),
 * 이 클래스는 시스템 알람의 등록/해제만 담당한다.
 */
class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    /** Android 12+ 에서는 사용자가 '알람 및 리마인더' 권한을 허용해야 정확한 알람을 등록할 수 있다. */
    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    /**
     * 알람을 (재)등록한다. 권한이 없으면 등록하지 않는다 — 이때도 pending_alarms 에는 기록이 남아 있어,
     * 권한을 허용한 뒤 앱이 다시 열리면 등록(또는 시각이 지났다면 놓친 알림으로 처리)된다. UI 에서 안내 배너를 보여준다.
     */
    fun set(taskId: Long, snooze: Boolean, epochDay: Long, triggerAtMillis: Long) {
        if (!canScheduleExact()) return
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent(taskId, snooze, epochDay),
        )
    }

    fun cancel(taskId: Long, snooze: Boolean) {
        alarmManager.cancel(pendingIntent(taskId, snooze, epochDay = 0))
    }

    /** 정규·다시알림 알람을 모두 해제한다. */
    fun cancelAll(taskId: Long) {
        cancel(taskId, snooze = false)
        cancel(taskId, snooze = true)
    }

    private fun pendingIntent(taskId: Long, snooze: Boolean, epochDay: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .putExtra(EXTRA_TASK_ID, taskId)
            .putExtra(EXTRA_EPOCH_DAY, epochDay)
            .putExtra(EXTRA_SNOOZE, snooze)
        val requestCode = (taskId * 2 + if (snooze) 1 else 0).toInt()
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
