package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        UserProfileEntity::class,
        ChatThreadEntity::class,
        ChatMessageEntity::class,
        SafetyReportEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class TimeDatabase : RoomDatabase() {
    abstract fun timeDao(): TimeDao

    companion object {
        @Volatile
        private var INSTANCE: TimeDatabase? = null

        fun getInstance(context: Context): TimeDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    TimeDatabase::class.java,
                    "time_local_db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
