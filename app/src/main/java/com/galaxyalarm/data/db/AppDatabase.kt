package com.galaxyalarm.data.db

import android.content.Context
import android.os.Build
import android.os.UserManager
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 2,
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
        private const val DB_NAME = "galaxy_alarm.db"

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alarm_items ADD COLUMN fadeInSeconds INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun canOpen(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return true
            val app = context.applicationContext
            val deviceContext = app.createDeviceProtectedStorageContext()
            if (deviceContext.getDatabasePath(DB_NAME).exists()) return true
            val userManager = app.getSystemService(UserManager::class.java)
            return userManager.isUserUnlocked
        }

        fun isInDeviceProtectedStorage(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.N ||
                context.createDeviceProtectedStorageContext().getDatabasePath(DB_NAME).exists()

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                check(canOpen(context)) { "Alarm database is unavailable before first unlock" }
                val storageContext = prepareStorage(context.applicationContext)
                INSTANCE ?: Room.databaseBuilder(
                    storageContext,
                    AppDatabase::class.java,
                    DB_NAME
                ).addMigrations(MIGRATION_1_2).build().also { INSTANCE = it }
            }

        private fun prepareStorage(app: Context): Context {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return app
            val deviceContext = app.createDeviceProtectedStorageContext()
            val destination = deviceContext.getDatabasePath(DB_NAME)
            val source = app.getDatabasePath(DB_NAME)
            if (!destination.exists() && source.exists()) {
                check(deviceContext.moveDatabaseFrom(app, DB_NAME)) {
                    "Could not migrate alarm database to device-protected storage"
                }
            }
            return deviceContext
        }
    }
}
