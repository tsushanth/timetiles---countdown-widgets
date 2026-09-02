package com.factory.timetilescountdownwidgets.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [CountdownEvent::class], version = 1, exportSchema = false)
abstract class CountdownDatabase : RoomDatabase() {
    abstract fun countdownDao(): CountdownDao

    companion object {
        @Volatile
        private var INSTANCE: CountdownDatabase? = null

        fun getInstance(context: Context): CountdownDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    CountdownDatabase::class.java,
                    "timetiles.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
