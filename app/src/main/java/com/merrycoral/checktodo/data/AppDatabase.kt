package com.merrycoral.checktodo.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Category::class, RoutineTask::class, TaskLog::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun categoryDao(): CategoryDao
    abstract fun logDao(): LogDao

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

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "check_todo.db")
                .addMigrations(MIGRATION_1_2)
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
