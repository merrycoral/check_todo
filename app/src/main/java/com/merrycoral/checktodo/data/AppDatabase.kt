package com.merrycoral.checktodo.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Category::class, RoutineTask::class, TaskLog::class, PendingAlarm::class],
    version = 4,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun categoryDao(): CategoryDao
    abstract fun logDao(): LogDao
    abstract fun pendingAlarmDao(): PendingAlarmDao

    companion object {
        /** v1 → v2: 카테고리 색상 추가. 기본 카테고리는 기본 색, 나머지는 기본 팔레트 첫 색. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE categories ADD COLUMN color INTEGER NOT NULL DEFAULT ${CategoryPalette.DEFAULT_COLOR}")
                CategoryPalette.DEFAULT_CATEGORIES.forEach { (name, color) ->
                    db.execSQL("UPDATE categories SET color = $color WHERE name = '$name'")
                }
            }
        }

        /** v2 → v3: 예약된 알람 기록 표 추가 (재부팅 후 다시알림 복구·놓친 알림 감지용). */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pending_alarms` (" +
                        "`taskId` INTEGER NOT NULL, `snooze` INTEGER NOT NULL, " +
                        "`epochDay` INTEGER NOT NULL, `triggerAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`taskId`, `snooze`), " +
                        "FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
            }
        }

        /**
         * v3 → v4: 알림 시각을 여러 개 둘 수 있게 하고(tasks.hour/minute → times) 메모를 추가한다.
         * 회차를 시각 단위로 구분하도록 task_logs·pending_alarms 에 slot 을 넣는다.
         * 기존 데이터는 시각 하나짜리 할 일로 그대로 옮긴다.
         *
         * 표 구조가 바뀌므로 세 표를 다시 만든다. 자식 표(pending_alarms, task_logs)를 먼저 지우고 tasks 를 지워야
         * 외래키 CASCADE 로 데이터가 함께 사라지는 일이 없다. 그래서 먼저 값을 모두 읽어 둔다.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val slotByTask = HashMap<Long, Int>()
                val tasks = ArrayList<ContentValues>()
                db.query("SELECT id, title, categoryId, repeatType, weekDays, monthDay, hour, minute, enabled FROM tasks").use { c ->
                    while (c.moveToNext()) {
                        val id = c.getLong(0)
                        val slot = c.getInt(6) * 60 + c.getInt(7)
                        slotByTask[id] = slot
                        tasks += ContentValues().apply {
                            put("id", id)
                            put("title", c.getString(1))
                            put("categoryId", c.getLong(2))
                            put("repeatType", c.getString(3))
                            put("weekDays", c.getInt(4))
                            put("monthDay", c.getInt(5))
                            put("times", slot.toString())
                            put("memo", "")
                            put("enabled", c.getInt(8))
                        }
                    }
                }
                val logs = ArrayList<ContentValues>()
                db.query("SELECT taskId, epochDay, status, loggedAt FROM task_logs").use { c ->
                    while (c.moveToNext()) {
                        val taskId = c.getLong(0)
                        logs += ContentValues().apply {
                            put("taskId", taskId)
                            put("epochDay", c.getLong(1))
                            put("slot", checkNotNull(slotByTask[taskId]))
                            put("status", c.getString(2))
                            put("loggedAt", c.getLong(3))
                        }
                    }
                }
                val alarms = ArrayList<ContentValues>()
                db.query("SELECT taskId, snooze, epochDay, triggerAt FROM pending_alarms").use { c ->
                    while (c.moveToNext()) {
                        val taskId = c.getLong(0)
                        alarms += ContentValues().apply {
                            put("taskId", taskId)
                            put("snooze", c.getInt(1))
                            put("slot", checkNotNull(slotByTask[taskId]))
                            put("epochDay", c.getLong(2))
                            put("triggerAt", c.getLong(3))
                        }
                    }
                }

                db.execSQL("DROP TABLE `pending_alarms`")
                db.execSQL("DROP TABLE `task_logs`")
                db.execSQL("DROP TABLE `tasks`")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tasks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`title` TEXT NOT NULL, `categoryId` INTEGER NOT NULL, `repeatType` TEXT NOT NULL, " +
                        "`weekDays` INTEGER NOT NULL, `monthDay` INTEGER NOT NULL, `times` TEXT NOT NULL, " +
                        "`memo` TEXT NOT NULL, `enabled` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_categoryId` ON `tasks` (`categoryId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `task_logs` (`taskId` INTEGER NOT NULL, `epochDay` INTEGER NOT NULL, " +
                        "`slot` INTEGER NOT NULL, `status` TEXT NOT NULL, `loggedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`taskId`, `epochDay`, `slot`), " +
                        "FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pending_alarms` (`taskId` INTEGER NOT NULL, `snooze` INTEGER NOT NULL, " +
                        "`slot` INTEGER NOT NULL, `epochDay` INTEGER NOT NULL, `triggerAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`taskId`, `snooze`, `slot`), " +
                        "FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                tasks.forEach { db.insert("tasks", SQLiteDatabase.CONFLICT_ABORT, it) }
                logs.forEach { db.insert("task_logs", SQLiteDatabase.CONFLICT_ABORT, it) }
                alarms.forEach { db.insert("pending_alarms", SQLiteDatabase.CONFLICT_ABORT, it) }
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "check_todo.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        CategoryPalette.DEFAULT_CATEGORIES.forEach { (name, color) ->
                            db.execSQL("INSERT INTO categories(name, color) VALUES ('$name', $color)")
                        }
                    }
                })
                .build()
    }
}
