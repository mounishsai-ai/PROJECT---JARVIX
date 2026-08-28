package com.example.aisecretary

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aisecretary.theme.AISecretaryTheme
import com.example.aisecretary.ui.main.MainScreen
import com.example.aisecretary.ui.main.MainScreenViewModel
import com.example.aisecretary.ui.routine.RoutineScreen
import com.example.aisecretary.ui.routine.RoutineViewModel
import com.example.aisecretary.ui.settings.SettingsScreen
import com.example.aisecretary.ui.settings.SettingsViewModel

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val permissionsToRequest = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(android.app.AlarmManager::class.java)
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
        }

        // Android 14 (API 34) stopped granting USE_FULL_SCREEN_INTENT automatically to most
        // apps. Without it, an alarm cannot take over a locked screen - it degrades to a
        // normal notification. Send the user to the toggle if we do not have it.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val notificationManager = getSystemService(android.app.NotificationManager::class.java)
            if (notificationManager != null && !notificationManager.canUseFullScreenIntent()) {
                try {
                    startActivity(
                        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                            data = Uri.fromParts("package", packageName, null)
                        }
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        enableEdgeToEdge()
        setContent {
            AISecretaryTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var currentScreen by remember { mutableStateOf("Main") }
                    val isAssistantLaunch = intent?.action == Intent.ACTION_ASSIST

                    val app = application as JarvixApplication
                    
                    if (currentScreen == "Main") {
                        val mainViewModel: MainScreenViewModel = viewModel(
                            factory = MainScreenViewModel.provideFactory(
                                app.taskRepository,
                                app.routineRepository,
                                app.settingsRepository,
                                // Application context, NOT the Activity: the ViewModel
                                // outlives this screen and would leak it on rotation.
                                applicationContext
                            )
                        )
                        MainScreen(
                            viewModel = mainViewModel,
                            isAssistantLaunch = isAssistantLaunch,
                            onNavigateToRoutines = { currentScreen = "Routine" },
                            onNavigateToSettings = { currentScreen = "Settings" }
                        )
                    } else if (currentScreen == "Routine") {
                        val routineViewModel: RoutineViewModel = viewModel(
                            factory = RoutineViewModel.provideFactory(app.routineRepository)
                        )
                        RoutineScreen(
                            viewModel = routineViewModel,
                            onBack = { currentScreen = "Main" }
                        )
                    } else if (currentScreen == "Settings") {
                        val settingsViewModel: SettingsViewModel = viewModel(
                            factory = SettingsViewModel.provideFactory(app.settingsRepository)
                        )
                        SettingsScreen(
                            viewModel = settingsViewModel,
                            onBack = { currentScreen = "Main" }
                        )
                    }
                }
            }
        }
    }
}
