package com.merrycoral.checktodo.schedule

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.merrycoral.checktodo.MainActivity
import com.merrycoral.checktodo.R
import com.merrycoral.checktodo.data.RoutineTask
import com.merrycoral.checktodo.data.slotLabel

const val ACTION_DONE = "com.merrycoral.checktodo.action.DONE"
const val ACTION_SKIP = "com.merrycoral.checktodo.action.SKIP"
const val ACTION_SNOOZE = "com.merrycoral.checktodo.action.SNOOZE"

object NotificationHelper {
    private const val CHANNEL_ID = "routine_reminders"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = context.getString(R.string.channel_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    // POST_NOTIFICATIONS 권한은 앱 실행 시 UI 에서 요청하며, 거부 상태는 배너로 안내한다.
    @SuppressLint("MissingPermission")
    fun show(context: Context, task: RoutineTask, categoryName: String, epochDay: Long, slot: Int) {
        val openApp = PendingIntent.getActivity(
            context,
            requestCode(task.id, slot, 3),
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val info = "$categoryName · ${slotLabel(slot)}"
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(task.title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.action_done), actionIntent(context, task.id, epochDay, slot, ACTION_DONE, 0))
            .addAction(0, context.getString(R.string.action_skip), actionIntent(context, task.id, epochDay, slot, ACTION_SKIP, 1))
            .addAction(0, context.getString(R.string.action_snooze), actionIntent(context, task.id, epochDay, slot, ACTION_SNOOZE, 2))
        // 메모가 있으면 본문에 메모를, 카테고리·시각은 헤더 옆에 보여준다.
        if (task.memo.isBlank()) {
            builder.setContentText(info)
        } else {
            builder.setContentText(task.memo)
                .setSubText(info)
                .setStyle(NotificationCompat.BigTextStyle().bigText(task.memo))
        }
        NotificationManagerCompat.from(context).notify(notificationId(task.id, slot), builder.build())
    }

    fun cancel(context: Context, taskId: Long, slot: Int) {
        NotificationManagerCompat.from(context).cancel(notificationId(taskId, slot))
    }

    private fun actionIntent(
        context: Context,
        taskId: Long,
        epochDay: Long,
        slot: Int,
        action: String,
        actionIndex: Int,
    ): PendingIntent {
        val intent = Intent(context, ActionReceiver::class.java)
            .setAction(action)
            .putExtra(EXTRA_TASK_ID, taskId)
            .putExtra(EXTRA_EPOCH_DAY, epochDay)
            .putExtra(EXTRA_SLOT, slot)
        return PendingIntent.getBroadcast(
            context,
            requestCode(taskId, slot, actionIndex),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** 회차(할 일 + 시각)마다 알림 하나. */
    private fun notificationId(taskId: Long, slot: Int): Int = (taskId * 1440 + slot).toInt()

    /** 회차마다, 그리고 버튼(0..2: 수행·건너뛰기·다시알림, 3: 앱 열기)마다 다른 요청 코드 */
    private fun requestCode(taskId: Long, slot: Int, index: Int): Int = notificationId(taskId, slot) * 4 + index
}
