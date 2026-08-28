package com.example.aisecretary.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Update
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "active_tasks")
data class ActiveTask(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val targetTimeIso8601: String, // Exact time to ring
    val reasoning: String, // Context why this time was chosen
    val isCompleted: Boolean = false
)

@Dao
interface ActiveTaskDao {
    @Query("SELECT * FROM active_tasks WHERE isCompleted = 0 ORDER BY targetTimeIso8601 ASC")
    fun getPendingTasks(): Flow<List<ActiveTask>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: ActiveTask): Long

    @Query("UPDATE active_tasks SET isCompleted = 1 WHERE id = :taskId")
    suspend fun markAsCompleted(taskId: Int): Int
    
    @Query("SELECT * FROM active_tasks WHERE id = :taskId")
    suspend fun getTaskById(taskId: Int): ActiveTask?
    
    @Update
    suspend fun updateTask(task: ActiveTask)
    
    @Query("DELETE FROM active_tasks WHERE id = :taskId")
    suspend fun deleteTask(taskId: Int): Int

    /** Housekeeping: drop long-finished tasks so the database does not grow forever. */
    @Query("DELETE FROM active_tasks WHERE isCompleted = 1 AND targetTimeIso8601 < :cutoffIso")
    suspend fun purgeOldCompleted(cutoffIso: String): Int

    @Query("DELETE FROM active_tasks")
    suspend fun clearAll(): Int
}
