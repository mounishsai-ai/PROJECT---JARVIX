package com.example.aisecretary.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [DailyRoutine::class, ActiveTask::class], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dailyRoutineDao(): DailyRoutineDao
    abstract fun activeTaskDao(): ActiveTaskDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * v1 -> v2: adds a unique index on (dayOfWeek, startTime, activityName).
         *
         * Existing installs may already hold duplicate routine rows from repeated timetable
         * uploads, and a unique index cannot be created over duplicates - so we collapse them
         * first, keeping the lowest id of each group.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    DELETE FROM daily_routines
                    WHERE id NOT IN (
                        SELECT MIN(id) FROM daily_routines
                        GROUP BY dayOfWeek, startTime, activityName
                    )
                    """.trimIndent()
                )
                // Must match the schema Room generates (app/schemas/...) exactly.
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                    "`index_daily_routines_dayOfWeek_startTime_activityName` " +
                    "ON `daily_routines` (`dayOfWeek`, `startTime`, `activityName`)"
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "aisecretary_database"
                ).addMigrations(MIGRATION_1_2).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
