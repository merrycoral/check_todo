package com.merrycoral.checktodo.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Category::class, RoutineTask::class, TaskLog::class, PendingAlarm::class],
    version = 3,
    exportSchema = false,
)
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

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "check_todo.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
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
