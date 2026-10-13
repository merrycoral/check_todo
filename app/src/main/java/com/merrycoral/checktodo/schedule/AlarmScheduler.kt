package com.merrycoral.checktodo.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.merrycoral.checktodo.data.RoutineTask
import com.merrycoral.checktodo.data.nextTrigger
import java.time.LocalDateTime
import java.time.ZoneId

const val EXTRA_TASK_ID = "task_id"
const val EXTRA_EPOCH_DAY = "epoch_day"
const val EXTRA_SNOOZE = "snooze"

private const val SNOOZE_MILLIS = 60 * 60 * 1000L

class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    /** Android 12+ 에서는 사용자가 '알람 및 리마인더' 권한을 허용해야 정확한 알람을 등록할 수 있다. */
    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    /** 다음 정규 알림 하나를 (재)등록한다. 비활성 할 일은 정규 알람만 해제한다. 권한이 없으면 등록하지 않는다(UI 에서 안내). */
    fun schedule(task: RoutineTask) {
        alarmManager.cancel(pendingIntent(task.id, snooze = false, epochDay = 0))
        if (!task.enabled || !canScheduleExact()) return
        val next = task.nextTrigger(LocalDateTime.now())
        val triggerAt = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            pendingIntent(task.id, snooze = false, epochDay = next.toLocalDate().toEpochDay()),
        )
    }

    /** 현재 회차([epochDay])를 1시간 뒤에 다시 알린다. */
    fun scheduleSnooze(taskId: Long, epochDay: Long) {
        if (!canScheduleExact()) return
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + SNOOZE_MILLIS,
            pendingIntent(taskId, snooze = true, epochDay = epochDay),
        )
    }

    /** 정규·다시알림 알람을 모두 해제한다. */
    fun cancel(taskId: Long) {
        alarmManager.cancel(pendingIntent(taskId, snooze = false, epochDay = 0))
        alarmManager.cancel(pendingIntent(taskId, snooze = true, epochDay = 0))
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
