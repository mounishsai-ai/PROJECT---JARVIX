package com.example.aisecretary.data

import kotlinx.coroutines.flow.Flow

interface RoutineRepository {
    fun getAllRoutines(): Flow<List<DailyRoutine>>
    suspend fun insertRoutine(routine: DailyRoutine): Long
    suspend fun deleteRoutine(routineId: Int): Int
    suspend fun deleteByDay(day: String): Int
}

class DefaultRoutineRepository(private val dao: DailyRoutineDao) : RoutineRepository {
    override fun getAllRoutines() = dao.getAllRoutines()
    override suspend fun insertRoutine(routine: DailyRoutine) = dao.insertRoutine(routine)
    override suspend fun deleteRoutine(routineId: Int): Int = dao.deleteRoutine(routineId)
    override suspend fun deleteByDay(day: String): Int = dao.deleteByDay(day)
}
