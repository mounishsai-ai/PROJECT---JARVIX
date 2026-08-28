package com.example.aisecretary.ui.routine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.aisecretary.data.DailyRoutine
import com.example.aisecretary.data.RoutineRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RoutineViewModel(private val repository: RoutineRepository) : ViewModel() {

    val routines: StateFlow<List<DailyRoutine>> = repository.getAllRoutines()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addRoutine(dayOfWeek: String, startTime: String, endTime: String, activityName: String) {
        if (dayOfWeek.isBlank() || startTime.isBlank() || endTime.isBlank() || activityName.isBlank()) return
        viewModelScope.launch {
            repository.insertRoutine(
                DailyRoutine(
                    dayOfWeek = dayOfWeek,
                    startTime = startTime,
                    endTime = endTime,
                    activityName = activityName
                )
            )
        }
    }

    fun deleteRoutine(routineId: Int) {
        viewModelScope.launch {
            repository.deleteRoutine(routineId)
        }
    }

    companion object {
        fun provideFactory(repository: RoutineRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(RoutineViewModel::class.java)) {
                    return RoutineViewModel(repository) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }
}
