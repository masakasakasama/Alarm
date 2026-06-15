package com.galaxyalarm.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.galaxyalarm.data.dao.AlarmEventLogDao
import com.galaxyalarm.data.dao.AlarmGroupDao
import com.galaxyalarm.data.dao.AlarmItemDao
import com.galaxyalarm.data.dao.ScheduledOccurrenceDao
import com.galaxyalarm.data.entity.AlarmEventLog
import com.galaxyalarm.data.entity.AlarmGroup
import com.galaxyalarm.data.entity.AlarmItem
import com.galaxyalarm.data.entity.ScheduledOccurrence

@Database(
    entities = [AlarmGroup::class, AlarmItem::class, ScheduledOccurrence::class, AlarmEventLog::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun groupDao(): AlarmGroupDao
    abstract fun alarmDao(): AlarmItemDao
    abstract fun occurrenceDao(): ScheduledOccurrenceDao
    abstract fun eventLogDao(): AlarmEventLogDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "galaxy_alarm.db"
                ).build().also { INSTANCE = it }
            }
    }
}
