package com.example.aisecretary.data

import kotlinx.coroutines.flow.Flow

interface TaskRepository {
    fun getPendingTasks(): Flow<List<ActiveTask>>
    suspend fun insertTask(task: ActiveTask): Long
    suspend fun markAsCompleted(taskId: Int): Int
    suspend fun getTaskById(taskId: Int): ActiveTask?
    suspend fun updateTask(task: ActiveTask)
    suspend fun deleteTask(taskId: Int): Int
    suspend fun purgeOldCompleted(cutoffIso: String): Int
}

class DefaultTaskRepository(private val dao: ActiveTaskDao) : TaskRepository {
    override fun getPendingTasks() = dao.getPendingTasks()
    override suspend fun insertTask(task: ActiveTask) = dao.insertTask(task)
    override suspend fun markAsCompleted(taskId: Int): Int = dao.markAsCompleted(taskId)
    override suspend fun getTaskById(taskId: Int): ActiveTask? = dao.getTaskById(taskId)
    override suspend fun updateTask(task: ActiveTask) = dao.updateTask(task)
    override suspend fun deleteTask(taskId: Int): Int = dao.deleteTask(taskId)
    override suspend fun purgeOldCompleted(cutoffIso: String): Int = dao.purgeOldCompleted(cutoffIso)
}
