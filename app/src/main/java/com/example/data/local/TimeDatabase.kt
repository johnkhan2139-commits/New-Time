package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        UserProfileEntity::class,
        ChatThreadEntity::class,
        ChatMessageEntity::class,
        SafetyReportEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class TimeDatabase : RoomDatabase() {
    abstract fun timeDao(): TimeDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chat_threads ADD COLUMN isGroup INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE chat_threads ADD COLUMN membersCsv TEXT NOT NULL DEFAULT ''")
            }
        }

        @Volatile
        private var INSTANCE: TimeDatabase? = null

        fun getInstance(context: Context): TimeDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    TimeDatabase::class.java,
                    "time_local_db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
