package com.example.aisecretary.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "daily_routines",
    indices = [
        // Stops the same timetable row being inserted twice. Combined with
        // OnConflictStrategy.REPLACE, a re-upload now overwrites instead of duplicating.
        Index(value = ["dayOfWeek", "startTime", "activityName"], unique = true)
    ]
)
data class DailyRoutine(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val dayOfWeek: String, // e.g., "Monday"
    val startTime: String, // e.g., "09:00"
    val endTime: String,   // e.g., "16:00"
    val activityName: String // e.g., "College", "Office"
)

@Dao
interface DailyRoutineDao {
    @Query("SELECT * FROM daily_routines ORDER BY id ASC")
    fun getAllRoutines(): Flow<List<DailyRoutine>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutine(routine: DailyRoutine): Long

    @Query("DELETE FROM daily_routines WHERE id = :routineId")
    suspend fun deleteRoutine(routineId: Int): Int

    /** Used before importing a timetable photo so a re-upload replaces that day cleanly. */
    @Query("DELETE FROM daily_routines WHERE dayOfWeek = :day COLLATE NOCASE")
    suspend fun deleteByDay(day: String): Int
}
