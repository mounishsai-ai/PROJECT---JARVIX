package com.example.aisecretary.ui.settings

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.aisecretary.theme.AquaAccent
import com.example.aisecretary.theme.ArcBlue
import com.example.aisecretary.theme.ArcBlueDim
import com.example.aisecretary.theme.CardNavy
import com.example.aisecretary.theme.CardNavyElevated
import com.example.aisecretary.theme.DeepNavy
import com.example.aisecretary.theme.ErrorRed
import com.example.aisecretary.theme.IronGold
import com.example.aisecretary.theme.SuccessGreen
import com.example.aisecretary.theme.SurfaceNavy
import com.example.aisecretary.theme.TextMuted
import com.example.aisecretary.theme.TextPrimary
import com.example.aisecretary.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("System Configuration", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = ArcBlue)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceNavy.copy(alpha = 0.95f))
            )
        },
        containerColor = DeepNavy,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                SystemStatusSection()
            }

            item {
                SettingsSection(title = "AMBIENT INTERFACE") {
                    SettingsSwitchRow(
                        label = "HUD ambient sound",
                        description = "Quiet background hum on the main screen. Always pauses while listening or speaking.",
                        checked = state.ambientSoundEnabled,
                        onCheckedChange = viewModel::setAmbientSoundEnabled,
                    )
                }
            }

            item {
                SettingsSection(title = "ALARM BEHAVIOR") {
                    SettingsSwitchRow(
                        label = "Play sound when phone is silenced",
                        description = "Off by default: silent or vibrate mode means the alarm vibrates only, never forces sound.",
                        checked = state.alarmSoundInSilentMode,
                        onCheckedChange = viewModel::setAlarmSoundInSilentMode,
                    )
                }
            }

            item {
                SettingsSection(title = "SNOOZE DURATIONS") {
                    Text(
                        "Minutes for each snooze option on the alarm screen. Each value must stay " +
                            "between its neighbors (Short < Medium < Long) - an entry that breaks " +
                            "that order is not saved.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SnoozeMinutesField(
                            label = "Short",
                            value = state.snoozeMinutesShort,
                            onValueChange = viewModel::setSnoozeMinutesShort,
                            modifier = Modifier.weight(1f),
                        )
                        SnoozeMinutesField(
                            label = "Medium",
                            value = state.snoozeMinutesMedium,
                            onValueChange = viewModel::setSnoozeMinutesMedium,
                            modifier = Modifier.weight(1f),
                        )
                        SnoozeMinutesField(
                            label = "Long",
                            value = state.snoozeMinutesLong,
                            onValueChange = viewModel::setSnoozeMinutesLong,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            item {
                Text(
                    "JARVIX",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            title,
            color = IronGold,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 8.dp, start = 4.dp),
        )
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardNavy),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(description, color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = ArcBlue,
                checkedTrackColor = ArcBlueDim,
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = CardNavyElevated,
            ),
        )
    }
}

@Composable
private fun SnoozeMinutesField(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Local text buffer so the field can be edited freely (including briefly empty) without
    // every keystroke needing to resolve to a valid, ordered snooze value.
    var text by remember(value) { mutableStateOf(value.toString()) }

    OutlinedTextField(
        value = text,
        onValueChange = { new ->
            text = new
            new.toIntOrNull()?.let(onValueChange)
        },
        label = { Text(label, color = TextMuted, fontSize = 12.sp) },
        suffix = { Text("m", color = TextMuted) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = ArcBlue,
            unfocusedBorderColor = ArcBlueDim,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextSecondary,
            cursorColor = ArcBlue,
        ),
        modifier = modifier,
    )
}

// ── System status: real permission checks, not a decorative panel ───────────
//
// Every check here mirrors the exact one MainActivity.kt runs on launch (canScheduleExactAlarms,
// canUseFullScreenIntent, RECORD_AUDIO/POST_NOTIFICATIONS). A permission silently revoked between
// launches - Android does this on its own after long inactivity - would otherwise only surface
// as an alarm or the mic quietly failing mid-demo. This makes that state checkable and fixable
// before it happens instead.

private data class SystemStatus(
    val micGranted: Boolean,
    val notificationsGranted: Boolean,
    val exactAlarmsAllowed: Boolean,
    val fullScreenIntentAllowed: Boolean,
)

private fun readSystemStatus(context: Context): SystemStatus {
    val mic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

    val notifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    } else true

    val exactAlarms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() ?: true
    } else true

    val fullScreenIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        context.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() ?: true
    } else true

    return SystemStatus(mic, notifications, exactAlarms, fullScreenIntent)
}

@Composable
private fun SystemStatusSection() {
    val context = LocalContext.current
    var status by remember { mutableStateOf(readSystemStatus(context)) }

    // Fixing any of these means leaving the app for a system Settings screen. Re-read on every
    // return trip so the panel reflects what's actually true, not a stale first-launch snapshot.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) status = readSystemStatus(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val appDetails = {
        context.startActivity(
            Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", context.packageName, null))
        )
    }

    SettingsSection(title = "SYSTEM STATUS") {
        StatusRow("Microphone", status.micGranted, "Voice commands need this to work at all.", appDetails)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            StatusRow(
                "Notifications", status.notificationsGranted,
                "Without this, the alarm can't show at all.", appDetails,
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            StatusRow(
                "Exact alarms", status.exactAlarmsAllowed,
                "Without this, reminders can fire minutes late or not at all.",
            ) {
                context.startActivity(
                    Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                        .setData(Uri.fromParts("package", context.packageName, null))
                )
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            StatusRow(
                "Full-screen alarm", status.fullScreenIntentAllowed,
                "Without this, alarms degrade to a quiet notification on a locked screen.",
            ) {
                context.startActivity(
                    Intent(AndroidSettings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                        .setData(Uri.fromParts("package", context.packageName, null))
                )
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, ok: Boolean, fixDescription: String, onFix: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (!ok) {
                Spacer(Modifier.height(2.dp))
                Text(fixDescription, color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
        Text(
            text = if (ok) "ONLINE" else "OFFLINE",
            color = if (ok) SuccessGreen else ErrorRed,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
        if (!ok) {
            TextButton(onClick = onFix) {
                Text("FIX", color = ArcBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
