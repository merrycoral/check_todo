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
import com.merrycoral.checktodo.data.timeLabel

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
    fun show(context: Context, task: RoutineTask, categoryName: String, epochDay: Long) {
        val openApp = PendingIntent.getActivity(
            context,
            requestCode(task.id, 3),
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(task.title)
            .setContentText("$categoryName · ${task.timeLabel()}")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.action_done), actionIntent(context, task.id, epochDay, ACTION_DONE, 0))
            .addAction(0, context.getString(R.string.action_skip), actionIntent(context, task.id, epochDay, ACTION_SKIP, 1))
            .addAction(0, context.getString(R.string.action_snooze), actionIntent(context, task.id, epochDay, ACTION_SNOOZE, 2))
            .build()
        NotificationManagerCompat.from(context).notify(task.id.toInt(), notification)
    }

    fun cancel(context: Context, taskId: Long) {
        NotificationManagerCompat.from(context).cancel(taskId.toInt())
    }

    private fun actionIntent(context: Context, taskId: Long, epochDay: Long, action: String, slot: Int): PendingIntent {
        val intent = Intent(context, ActionReceiver::class.java)
            .setAction(action)
            .putExtra(EXTRA_TASK_ID, taskId)
            .putExtra(EXTRA_EPOCH_DAY, epochDay)
        return PendingIntent.getBroadcast(
            context,
            requestCode(taskId, slot),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun requestCode(taskId: Long, slot: Int): Int = (taskId * 4 + slot).toInt()
}
