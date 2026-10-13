package com.merrycoral.checktodo

import android.app.Application
import com.merrycoral.checktodo.data.AppDatabase
import com.merrycoral.checktodo.data.Repository
import com.merrycoral.checktodo.schedule.AlarmScheduler
import com.merrycoral.checktodo.schedule.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class CheckTodoApp : Application() {
    /** BroadcastReceiver 등 UI 밖에서 쓰는 앱 수명 스코프 */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var repository: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = Repository(this, AppDatabase.create(this), AlarmScheduler(this))
        NotificationHelper.createChannel(this)
    }
}
