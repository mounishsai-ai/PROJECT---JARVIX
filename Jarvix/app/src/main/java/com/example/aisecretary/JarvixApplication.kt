package com.example.aisecretary

import android.app.Application
import com.example.aisecretary.alarms.AlarmNotifier
import com.example.aisecretary.data.AppDatabase
import com.example.aisecretary.data.DefaultRoutineRepository
import com.example.aisecretary.data.DefaultTaskRepository
import com.example.aisecretary.data.RoutineRepository
import com.example.aisecretary.data.SettingsRepository
import com.example.aisecretary.data.TaskRepository

class JarvixApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Register the alarm notification channel once, at startup, so the very first
        // alarm can post a full-screen intent without racing channel creation.
        AlarmNotifier.ensureChannel(this)
    }

    val database by lazy { AppDatabase.getDatabase(this) }
    val taskRepository: TaskRepository by lazy {
        DefaultTaskRepository(database.activeTaskDao())
    }
    val routineRepository: RoutineRepository by lazy {
        DefaultRoutineRepository(database.dailyRoutineDao())
    }
    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(this)
    }
}
