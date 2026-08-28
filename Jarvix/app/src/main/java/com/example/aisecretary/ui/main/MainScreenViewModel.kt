package com.example.aisecretary.ui.main

import android.content.Context
import android.content.pm.PackageManager
import android.util.Base64
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.aisecretary.ai.VertexAi
import com.example.aisecretary.alarms.AlarmNotifier
import com.example.aisecretary.alarms.AlarmUtils
import com.example.aisecretary.audio.AudioRecorderHelper
import com.example.aisecretary.audio.GeminiTTSHelper
import com.example.aisecretary.data.ActiveTask
import com.example.aisecretary.data.RoutineRepository
import com.example.aisecretary.data.SettingsRepository
import com.example.aisecretary.data.TaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

sealed interface DashboardUiState {
    object Loading : DashboardUiState
    data class Success(val tasks: List<ActiveTask>) : DashboardUiState
    data class Error(val throwable: Throwable) : DashboardUiState
}

class MainScreenViewModel(
    private val taskRepository: TaskRepository,
    private val routineRepository: RoutineRepository,
    private val settingsRepository: SettingsRepository,
    private val context: Context
) : ViewModel() {

    private val ttsHelper = GeminiTTSHelper(context)

    init {
        purgeOldCompletedTasks()
    }

    override fun onCleared() {
        super.onCleared()
        ttsHelper.shutdown()
    }

    val uiState: StateFlow<DashboardUiState> = taskRepository.getPendingTasks()
        .map { DashboardUiState.Success(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DashboardUiState.Loading
        )

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _lastResponse = MutableStateFlow<String?>(null)
    val lastResponse: StateFlow<String?> = _lastResponse

    private val _micLevel = MutableStateFlow(0f)

    /** Real mic amplitude, 0f..1f, sampled while recording. Drives the reactor - not simulated. */
    val micLevel: StateFlow<Float> = _micLevel

    /** True only while audio is actually being played back to the user. */
    val isSpeaking: StateFlow<Boolean> = ttsHelper.isSpeaking

    private val _ambientSoundEnabled = MutableStateFlow(settingsRepository.ambientSoundEnabled)

    /** Persisted setting - survives app restarts, unlike a plain UI-only toggle. */
    val ambientSoundEnabled: StateFlow<Boolean> = _ambientSoundEnabled

    fun setAmbientSoundEnabled(enabled: Boolean) {
        settingsRepository.ambientSoundEnabled = enabled
        _ambientSoundEnabled.value = enabled
    }

    private var audioRecorder: AudioRecorderHelper? = null
    private var recordingStartTime: Long = 0
    private var micLevelJob: Job? = null

    private val systemInstructionText = """
        You are JARVIS — a highly advanced, proactive, and slightly sarcastic AI life copilot built by Tony Stark.
        You speak with calm confidence, dry wit, and a subtle British undertone.
        
        Listen carefully to the user's audio command and extract any actionable task or reminder they mention.
        You are provided with the user's weekly routine timetable and their currently pending tasks.
        If they use relative time expressions like "after college", "before work", or "in an hour", calculate the
        exact ISO-8601 timestamp by consulting their routine and the Current System Time provided.
        
        YOUR RESPONSE MUST FOLLOW THIS EXACT TWO-SECTION FORMAT — no exceptions:

        SECTION 1 — Conversational Reply (this will be spoken aloud):
        Write a short, in-character JARVIS reply. Be brief (1-2 sentences). Confirm what you did, add a touch of dry wit.
        Do NOT include any JSON, timestamps, or technical information in this section.
        
        Then on its own line, write exactly:
        ---JSON---
        
        SECTION 2 — Structured Data (silent, never spoken):
        Immediately after the delimiter, write ONLY a raw JSON object with NO markdown, NO backticks.
        {
          "task_title": "Short title of the task",
          "target_time_iso8601": "ISO-8601 timestamp or null if no time needed"
        }
    """.trimIndent()

    fun initAudioRecorder(ctx: Context) {
        if (audioRecorder == null) {
            audioRecorder = AudioRecorderHelper(ctx)
        }
    }

    fun startRecording() {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            _lastResponse.value = "Microphone permission denied. Please grant it in Settings."
            return
        }
        recordingStartTime = System.currentTimeMillis()
        _isRecording.value = true
        try {
            audioRecorder?.startRecording()
            // Poll the real mic level while listening so the reactor's glow reflects the
            // user's actual voice, not a fake breathing animation.
            micLevelJob = viewModelScope.launch {
                while (isActive) {
                    val amplitude = audioRecorder?.getMaxAmplitude() ?: 0
                    // 16-bit PCM amplitude tops out near 32767.
                    _micLevel.value = (amplitude / 32767f).coerceIn(0f, 1f)
                    delay(50)
                }
            }
        } catch (e: Exception) {
            _lastResponse.value = "Failed to start recording: ${e.message}"
            _isRecording.value = false
        }
    }

    fun stopRecording(ctx: Context) {
        _isRecording.value = false
        micLevelJob?.cancel()
        micLevelJob = null
        _micLevel.value = 0f
        audioRecorder?.stopRecording()

        val duration = System.currentTimeMillis() - recordingStartTime
        if (duration < 800) {
            _lastResponse.value = "Please press and HOLD the microphone to speak."
            return
        }

        val audioFile = audioRecorder?.getAudioFile()
        if (audioFile != null) {
            val bytes = audioFile.readBytes()
            audioRecorder?.discardCurrentFile()
            processAudioFile(bytes)
        } else {
            _lastResponse.value = "I didn't catch any audio. Hold the mic and speak."
        }
    }

    private fun processAudioFile(audioBytes: ByteArray) {
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                val now = LocalDateTime.now(ZoneId.systemDefault())
                val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
                val currentTime = now.format(formatter)

                val routines = routineRepository.getAllRoutines().first()
                val routineContext = routines.joinToString("\n") {
                    "- ${it.dayOfWeek}: ${it.startTime} to ${it.endTime} (${it.activityName})"
                }

                // Golden rule: prune expired context before sending. A task whose time has
                // already passed is not an active commitment - feeding it to Gemini invites
                // hallucinated scheduling around things that already happened.
                val currentState = uiState.value
                val currentTasks = if (currentState is DashboardUiState.Success) {
                    currentState.tasks
                        .filter { !isInPast(it.targetTimeIso8601) }
                        .joinToString("\n") { "- ${it.title} at ${it.targetTimeIso8601}" }
                } else ""

                val promptText = """
                    Current System Time: $currentTime
                    
                    User's Weekly Routine:
                    ${if (routineContext.isBlank()) "No routines set." else routineContext}
                    
                    Pending Tasks:
                    ${if (currentTasks.isBlank()) "No pending tasks." else currentTasks}
                """.trimIndent()

                val spokenText = withContext(Dispatchers.IO) {
                    // Call 1: Process User's Audio and Get Text Response
                    val url1 = URL(VertexAi.generateContentUrl("gemini-3.6-flash"))
                    val conn1 = url1.openConnection() as HttpURLConnection
                    conn1.requestMethod = "POST"
                    conn1.setRequestProperty("Content-Type", "application/json")
                    conn1.connectTimeout = 30_000
                    conn1.readTimeout = 60_000
                    conn1.doOutput = true
                    
                    val sysInst1 = JSONObject()
                    sysInst1.put("parts", JSONArray().put(JSONObject().put("text", systemInstructionText)))
                    
                    val parts1 = JSONArray()
                    parts1.put(JSONObject().put("text", promptText))
                    val inlineData1 = JSONObject()
                    inlineData1.put("mimeType", AudioRecorderHelper.MIME_TYPE)
                    inlineData1.put("data", Base64.encodeToString(audioBytes, Base64.NO_WRAP))
                    parts1.put(JSONObject().put("inlineData", inlineData1))
                    
                    val contentObj1 = JSONObject()
                    contentObj1.put("role", "user")
                    contentObj1.put("parts", parts1)
                    
                    val root1 = JSONObject()
                    root1.put("systemInstruction", sysInst1)
                    root1.put("contents", JSONArray().put(contentObj1))
                    
                    conn1.outputStream.use { os ->
                        os.write(root1.toString().toByteArray(Charsets.UTF_8))
                    }
                    
                    val responseCode1 = conn1.responseCode
                    val responseBody1 = if (responseCode1 in 200..299) {
                        conn1.inputStream.bufferedReader().use { it.readText() }
                    } else {
                        val err = conn1.errorStream.bufferedReader().use { it.readText() }
                        throw Exception("API Error 1 ($responseCode1): $err")
                    }
                    
                    val json1 = JSONObject(responseBody1)
                    val textContent = json1.optJSONArray("candidates")
                        ?.optJSONObject(0)
                        ?.optJSONObject("content")
                        ?.optJSONArray("parts")
                        ?.optJSONObject(0)
                        ?.optString("text")
                        ?: throw Exception("No text in response 1")
                    textContent
                }

                val delimiter = "---JSON---"
                val spokenPart: String
                val jsonPart: String

                if (spokenText.contains(delimiter)) {
                    val split = spokenText.split(delimiter, limit = 2)
                    spokenPart = split[0].trim()
                    jsonPart = split[1].trim()
                } else {
                    spokenPart = ""
                    jsonPart = spokenText.trim()
                }

                _lastResponse.value = spokenPart.ifBlank { "Processing complete." }

                if (spokenPart.isNotBlank()) {
                    ttsHelper.speak(spokenPart)
                }

                if (jsonPart.isNotBlank()) {
                    try {
                        val cleanJson = jsonPart.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                        val json = JSONObject(cleanJson)
                        val title = json.optString("task_title", "Unknown Task")
                        val timeStr = json.optString("target_time_iso8601", "")

                        if (timeStr.isNotBlank() && timeStr != "null") {
                            val task = ActiveTask(
                                title = title,
                                targetTimeIso8601 = timeStr,
                                reasoning = spokenPart
                            )
                            val taskId = taskRepository.insertTask(task).toInt()
                            try {
                                val ldt = LocalDateTime.parse(timeStr)
                                val millis = ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                AlarmUtils.scheduleExactAlarm(context, millis, taskId)
                            } catch (e: Exception) {
                                // A task sitting in the list with no working alarm behind it
                                // would look scheduled while silently never firing - the JARVIS
                                // reply may already have confirmed it verbally by this point, but
                                // the visible task list and status text should not repeat that lie.
                                e.printStackTrace()
                                taskRepository.deleteTask(taskId)
                                _lastResponse.value =
                                    "$spokenPart\n\n[Could not set an alarm for that time - please try rephrasing it.]"
                            }
                        }
                    } catch (e: Exception) {
                        // The model's structured half of the reply didn't parse - nothing was
                        // saved. Say so rather than let a spoken confirmation go unbacked by
                        // anything the user can see or check later.
                        e.printStackTrace()
                        _lastResponse.value =
                            "$spokenPart\n\n[Didn't catch the task details clearly - please repeat it.]"
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                _lastResponse.value = friendlyErrorMessage(e)
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun processTimetableImage(context: Context, uri: android.net.Uri) {
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                val imageBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (imageBytes == null) throw Exception("Could not read image")

                val promptText = """
                    Extract the weekly timetable or schedule from this image.
                    Output ONLY a raw JSON array of objects (no markdown, no backticks). 
                    Each object must have exactly these fields:
                    - "dayOfWeek" (String, e.g., "Monday")
                    - "startTime" (String, e.g., "09:00" in 24-hour format)
                    - "endTime" (String, e.g., "17:00" in 24-hour format)
                    - "activityName" (String, a short description)
                    
                    Also, at the very beginning before the JSON array, write a 1-2 sentence spoken confirmation 
                    in JARVIS's voice acknowledging the upload, followed by '---JSON---', and then the JSON array.
                """.trimIndent()

                val spokenText = withContext(Dispatchers.IO) {
                    val url1 = URL(VertexAi.generateContentUrl("gemini-3.6-flash"))
                    val conn1 = url1.openConnection() as HttpURLConnection
                    conn1.requestMethod = "POST"
                    conn1.setRequestProperty("Content-Type", "application/json")
                    conn1.connectTimeout = 30_000
                    conn1.readTimeout = 60_000
                    conn1.doOutput = true

                    val parts1 = JSONArray()
                    parts1.put(JSONObject().put("text", promptText))
                    val inlineData1 = JSONObject()
                    inlineData1.put("mimeType", mimeType)
                    inlineData1.put("data", Base64.encodeToString(imageBytes, Base64.NO_WRAP))
                    parts1.put(JSONObject().put("inlineData", inlineData1))

                    val contentObj1 = JSONObject()
                    contentObj1.put("role", "user")
                    contentObj1.put("parts", parts1)

                    val root1 = JSONObject()
                    root1.put("contents", JSONArray().put(contentObj1))

                    conn1.outputStream.use { os ->
                        os.write(root1.toString().toByteArray(Charsets.UTF_8))
                    }

                    val responseCode1 = conn1.responseCode
                    val responseBody1 = if (responseCode1 in 200..299) {
                        conn1.inputStream.bufferedReader().use { it.readText() }
                    } else {
                        val err = conn1.errorStream.bufferedReader().use { it.readText() }
                        throw Exception("API Error 1 ($responseCode1): $err")
                    }

                    val json1 = JSONObject(responseBody1)
                    val textContent = json1.optJSONArray("candidates")
                        ?.optJSONObject(0)
                        ?.optJSONObject("content")
                        ?.optJSONArray("parts")
                        ?.optJSONObject(0)
                        ?.optString("text")
                        ?: throw Exception("No text in response 1")

                    textContent
                }

                val delimiter = "---JSON---"
                val spokenPart: String
                val jsonPart: String

                if (spokenText.contains(delimiter)) {
                    val split = spokenText.split(delimiter, limit = 2)
                    spokenPart = split[0].trim()
                    jsonPart = split[1].trim()
                } else {
                    spokenPart = "Timetable processed."
                    jsonPart = spokenText.trim()
                }

                _lastResponse.value = spokenPart

                if (spokenPart.isNotBlank()) {
                    ttsHelper.speak(spokenPart)
                }

                if (jsonPart.isNotBlank()) {
                    try {
                        val cleanJson = jsonPart.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                        val jsonArray = JSONArray(cleanJson)

                        // Re-uploading a timetable must REPLACE that day, not stack another
                        // copy on top of it. Without this, photographing the same timetable
                        // twice doubles every class and Gemini then reasons over a
                        // contradictory schedule.
                        val daysReplacedThisImport = mutableSetOf<String>()

                        for (i in 0 until jsonArray.length()) {
                            val obj = jsonArray.getJSONObject(i)
                            val day = obj.optString("dayOfWeek", "")
                            val start = obj.optString("startTime", "")
                            val end = obj.optString("endTime", "")
                            val name = obj.optString("activityName", "")
                            if (day.isNotBlank() && start.isNotBlank()) {
                                if (daysReplacedThisImport.add(day.lowercase())) {
                                    routineRepository.deleteByDay(day)
                                }
                                val routine = com.example.aisecretary.data.DailyRoutine(
                                    dayOfWeek = day,
                                    startTime = start,
                                    endTime = end,
                                    activityName = name
                                )
                                routineRepository.insertRoutine(routine)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                _lastResponse.value = friendlyErrorMessage(e)
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun markDone(taskId: Int) {
        viewModelScope.launch {
            // Cancel BEFORE updating the DB: without this the alarm stayed armed and would
            // still fire for a task the user had already ticked off.
            AlarmUtils.cancelAlarm(context, taskId)
            AlarmNotifier.cancel(context, taskId)
            taskRepository.markAsCompleted(taskId)
        }
    }

    fun deleteTask(taskId: Int) {
        viewModelScope.launch {
            AlarmUtils.cancelAlarm(context, taskId)
            AlarmNotifier.cancel(context, taskId)
            taskRepository.deleteTask(taskId)
        }
    }

    /**
     * `UnknownHostException`/`SocketTimeoutException` etc. print as raw Java strings like
     * "Unable to resolve host..." - accurate but not something a demo audience should have to
     * read. Everything else keeps its real message; this only smooths the one failure mode
     * that's near-guaranteed on unreliable venue Wi-Fi.
     */
    private fun friendlyErrorMessage(e: Exception): String =
        if (e is IOException) "No connection to Jarvix's servers - check the network and try again."
        else "Error: ${e.message}"

    /** True if this task's time has already passed. Unparseable times count as not-past. */
    private fun isInPast(iso: String): Boolean =
        try {
            LocalDateTime.parse(iso).isBefore(LocalDateTime.now())
        } catch (e: Exception) {
            false
        }

    /** Drops completed tasks older than a week so the local database stays small. */
    private fun purgeOldCompletedTasks() {
        viewModelScope.launch {
            try {
                val cutoff = LocalDateTime.now().minusDays(7).toString()
                taskRepository.purgeOldCompleted(cutoff)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    companion object {
        fun provideFactory(
            taskRepository: TaskRepository,
            routineRepository: RoutineRepository,
            settingsRepository: SettingsRepository,
            context: Context
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(MainScreenViewModel::class.java)) {
                    return MainScreenViewModel(
                        taskRepository, routineRepository, settingsRepository, context
                    ) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }
}
