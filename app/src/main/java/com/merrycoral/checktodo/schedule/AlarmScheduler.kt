package com.merrycoral.checktodo.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

const val EXTRA_TASK_ID = "task_id"
const val EXTRA_EPOCH_DAY = "epoch_day"
const val EXTRA_SLOT = "slot"
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
     * 알람을 (재)등록한다. 정규 알람은 할 일마다 하나(다음 회차), 다시알림은 회차([slot])마다 하나다.
     * 권한이 없으면 등록하지 않는다 — 이때도 pending_alarms 에는 기록이 남아 있어,
     * 권한을 허용한 뒤 앱이 다시 열리면 등록(또는 시각이 지났다면 놓친 알림으로 처리)된다. UI 에서 안내 배너를 보여준다.
     */
    fun set(taskId: Long, snooze: Boolean, slot: Int, epochDay: Long, triggerAtMillis: Long) {
        if (!canScheduleExact()) return
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent(taskId, snooze, slot, epochDay),
        )
    }

    fun cancel(taskId: Long, snooze: Boolean, slot: Int) {
        alarmManager.cancel(pendingIntent(taskId, snooze, slot, epochDay = 0))
    }

    private fun pendingIntent(taskId: Long, snooze: Boolean, slot: Int, epochDay: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .putExtra(EXTRA_TASK_ID, taskId)
            .putExtra(EXTRA_EPOCH_DAY, epochDay)
            .putExtra(EXTRA_SLOT, slot)
            .putExtra(EXTRA_SNOOZE, snooze)
        return PendingIntent.getBroadcast(
            context,
            requestCode(taskId, snooze, slot),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** 정규 알람은 할 일마다 하나이므로 시각과 무관하게 고정, 다시알림은 회차(0..1439)마다 다르다. */
    private fun requestCode(taskId: Long, snooze: Boolean, slot: Int): Int =
        (taskId * REQUEST_CODES_PER_TASK + if (snooze) 2 + slot else 0).toInt()

    private companion object {
        const val REQUEST_CODES_PER_TASK = 1442L // 정규 1 + 예약 1 + 다시알림 1440
    }
}
