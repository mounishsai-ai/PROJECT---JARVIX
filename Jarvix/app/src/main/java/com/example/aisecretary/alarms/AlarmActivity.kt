package com.example.aisecretary.alarms

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.example.aisecretary.JarvixApplication
import com.example.aisecretary.data.ActiveTask
import com.example.aisecretary.theme.ArcBlue
import com.example.aisecretary.theme.DeepNavy
import com.example.aisecretary.theme.AISecretaryTheme
import com.example.aisecretary.theme.TextPrimary
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.roundToInt

class AlarmActivity : ComponentActivity() {

    companion object {
        /** Kept as the literal "ALARM_ID" so alarms scheduled by older builds still resolve. */
        const val EXTRA_ALARM_ID = "ALARM_ID"

        /** If the alarm stream is quieter than this fraction of max, we raise it. */
        private const val MIN_ALARM_VOLUME_FRACTION = 0.30
        private const val RAISED_ALARM_VOLUME_FRACTION = 0.60
    }

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var alarmId: Int = -1
    private var currentTask: ActiveTask? by mutableStateOf(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )

        alarmId = intent.getIntExtra(EXTRA_ALARM_ID, -1)
        val app = application as JarvixApplication
        val taskRepository = app.taskRepository
        val settings = app.settingsRepository

        lifecycleScope.launch {
            currentTask = taskRepository.getTaskById(alarmId)
        }

        startAlarmSound(settings.alarmSoundInSilentMode)
        startVibration()

        setContent {
            AISecretaryTheme {
                AlarmScreen(
                    task = currentTask,
                    snoozeMinutesShort = settings.snoozeMinutesShort.toLong(),
                    snoozeMinutesMedium = settings.snoozeMinutesMedium.toLong(),
                    snoozeMinutesLong = settings.snoozeMinutesLong.toLong(),
                    onSnooze = { minutes -> snoozeAlarm(alarmId, minutes) },
                    onDismiss = { dismissAlarm(alarmId) }
                )
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        // singleTask: a second delivery of the same alarm lands here instead of recreating.
        setIntent(intent)
    }

    /**
     * Plays the alarm on the ALARM audio stream - EXCEPT when the user has explicitly put the
     * phone in silent or vibrate mode, which is a deliberate product decision (2026-08-27):
     * silenced means vibrate-only, not "ring anyway". Vibration (started separately) still
     * always fires so the alarm is never completely silent-and-invisible.
     *
     * This is checked explicitly via AudioManager.ringerMode rather than left to chance,
     * because some OEM skins mute the ALARM stream under silent mode and some (stock AOSP)
     * do not - checking explicitly means this behaves the same on every phone.
     *
     * @param forceSoundInSilentMode Settings override - if the user has explicitly asked for
     *   sound even when silenced, skip the ringer-mode gate below.
     */
    private fun startAlarmSound(forceSoundInSilentMode: Boolean) {
        try {
            val audioManager = getSystemService(AudioManager::class.java)
            val ringerMode = audioManager?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL
            if (ringerMode != AudioManager.RINGER_MODE_NORMAL && !forceSoundInSilentMode) {
                return
            }

            ensureAlarmVolumeAudible()

            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: return

            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@AlarmActivity, alarmUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** An alarm nobody can hear is a failed alarm - nudge the stream up if it is near-silent. */
    private fun ensureAlarmVolumeAudible() {
        try {
            val audioManager = getSystemService(AudioManager::class.java) ?: return
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val current = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            if (max > 0 && current < (max * MIN_ALARM_VOLUME_FRACTION)) {
                audioManager.setStreamVolume(
                    AudioManager.STREAM_ALARM,
                    (max * RAISED_ALARM_VOLUME_FRACTION).toInt().coerceAtLeast(1),
                    0
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startVibration() {
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = getSystemService(VibratorManager::class.java)
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Vibrator::class.java)
            }

            // repeat index 0 -> loop the pattern until we cancel it
            val pattern = longArrayOf(0, 600, 600)
            val effect = VibrationEffect.createWaveform(pattern, 0)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // API 33+ uses VibrationAttributes; the AudioAttributes overload is deprecated.
                vibrator?.vibrate(
                    effect,
                    VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(
                    effect,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopAlarmFeedback() {
        try {
            mediaPlayer?.let { if (it.isPlaying) it.stop() }
            mediaPlayer?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mediaPlayer = null

        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        vibrator = null
    }

    private fun snoozeAlarm(taskId: Int, minutes: Long) {
        stopAlarmFeedback()
        AlarmNotifier.cancel(this, taskId)

        val snoozeTime = LocalDateTime.now().plusMinutes(minutes)
        val millis = snoozeTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        AlarmUtils.scheduleExactAlarm(this, millis, taskId)

        lifecycleScope.launch {
            val taskRepo = (application as JarvixApplication).taskRepository
            val task = taskRepo.getTaskById(taskId)
            if (task != null) {
                taskRepo.updateTask(task.copy(targetTimeIso8601 = snoozeTime.toString()))
            }
            finishAndRemoveTask()
        }
    }

    private fun dismissAlarm(taskId: Int) {
        stopAlarmFeedback()
        AlarmNotifier.cancel(this, taskId)

        lifecycleScope.launch {
            (application as JarvixApplication).taskRepository.markAsCompleted(taskId)
            finishAndRemoveTask()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlarmFeedback()
        AlarmNotifier.cancel(this, alarmId)
    }
}

@Composable
fun AlarmScreen(
    task: ActiveTask?,
    snoozeMinutesShort: Long,
    snoozeMinutesMedium: Long,
    snoozeMinutesLong: Long,
    onSnooze: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepNavy),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "JARVIX ALARM",
                color = ArcBlue,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Text(
                text = task?.title ?: "Reminder",
                color = TextPrimary,
                fontSize = 32.sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier.padding(bottom = 64.dp)
            )

            SwipeBubble(
                snoozeMinutesShort = snoozeMinutesShort,
                snoozeMinutesMedium = snoozeMinutesMedium,
                snoozeMinutesLong = snoozeMinutesLong,
                onSnooze = onSnooze,
                onDismiss = onDismiss,
            )
        }
    }
}

@Composable
fun SwipeBubble(
    snoozeMinutesShort: Long,
    snoozeMinutesMedium: Long,
    snoozeMinutesLong: Long,
    onSnooze: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    val maxDragDistance = 200f

    val scope = rememberCoroutineScope()
    val animatedOffsetX = remember { Animatable(0f) }
    val animatedOffsetY = remember { Animatable(0f) }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(300.dp)
    ) {
        // Labels
        Text("${snoozeMinutesShort}m", color = TextPrimary, modifier = Modifier.align(Alignment.TopCenter))
        Text("${snoozeMinutesLong}m", color = TextPrimary, modifier = Modifier.align(Alignment.BottomCenter))
        Text("${snoozeMinutesMedium}m", color = TextPrimary, modifier = Modifier.align(Alignment.CenterEnd))
        Text("Dismiss", color = Color(0xFFFF5252), modifier = Modifier.align(Alignment.CenterStart))

        // The draggable bubble
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .offset { IntOffset(animatedOffsetX.value.roundToInt(), animatedOffsetY.value.roundToInt()) }
                .size(80.dp)
                .background(ArcBlue.copy(alpha = 0.2f), CircleShape)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = {
                            val x = offsetX
                            val y = offsetY
                            
                            if (y < -maxDragDistance * 0.5f) onSnooze(snoozeMinutesShort)
                            else if (x > maxDragDistance * 0.5f) onSnooze(snoozeMinutesMedium)
                            else if (y > maxDragDistance * 0.5f) onSnooze(snoozeMinutesLong)
                            else if (x < -maxDragDistance * 0.5f) onDismiss()
                            
                            // Snap back
                            scope.launch {
                                animatedOffsetX.animateTo(0f, tween(300))
                                animatedOffsetY.animateTo(0f, tween(300))
                                offsetX = 0f
                                offsetY = 0f
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            offsetX += dragAmount.x
                            offsetY += dragAmount.y
                            
                            // Constrain drag to max distance
                            val distance = Math.sqrt((offsetX * offsetX + offsetY * offsetY).toDouble()).toFloat()
                            if (distance > maxDragDistance) {
                                val ratio = maxDragDistance / distance
                                offsetX *= ratio
                                offsetY *= ratio
                            }
                            
                            scope.launch {
                                animatedOffsetX.snapTo(offsetX)
                                animatedOffsetY.snapTo(offsetY)
                            }
                        }
                    )
                }
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .background(ArcBlue, CircleShape)
            )
        }
    }
}
