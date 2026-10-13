package com.merrycoral.checktodo.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Category::class, RoutineTask::class, TaskLog::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun categoryDao(): CategoryDao
    abstract fun logDao(): LogDao

    companion object {
        private val DEFAULT_CATEGORIES = listOf("건강", "금융", "업무", "운동", "뷰티")

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "check_todo.db")
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        DEFAULT_CATEGORIES.forEach { name ->
                            db.execSQL("INSERT INTO categories(name) VALUES ('$name')")
                        }
                    }
                })
                .build()
    }
}
