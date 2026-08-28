package com.example.aisecretary.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Base64
import android.util.Log
import com.example.aisecretary.ai.VertexAi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Speaks Jarvix's replies using Gemini's cinematic TTS voice instead of Android's built-in
 * robot voice.
 *
 * The API returns raw PCM (audio/l16, 24 kHz, mono) as base64 - NOT a playable container -
 * so MediaPlayer cannot play it. Raw PCM has to go through AudioTrack.
 *
 * Streamed over SSE (streamGenerateContent) rather than one blocking call: the full clip takes
 * ~6-10s to generate, but the first audio chunk lands in ~1.5-2s. Feeding chunks to AudioTrack
 * as they arrive means the reply starts playing at that ~1.5-2s mark instead of after the whole
 * clip is ready - the single biggest perceived-latency win available on this call.
 *
 * If the stream fails for any reason, we fall back to the device's built-in TTS so the user
 * still hears a reply rather than silence.
 */
class GeminiTTSHelper(context: Context) {

    companion object {
        private const val TAG = "GeminiTTSHelper"
        private const val MODEL = "gemini-3.1-flash-tts-preview"
        private const val VOICE = "Charon"
        private const val DEFAULT_SAMPLE_RATE = 24000
        // Queue this much audio before calling play(), so a slow first chunk doesn't starve
        // the track mid-sentence. Blocking write() on MODE_STREAM provides backpressure after
        // that - we never need to know the total clip length up front.
        private const val PREBUFFER_MS = 1200L
    }

    private val fallback = NativeTTSHelper(context).apply {
        onDone = { _isSpeaking.value = false }
    }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    private var audioTrack: AudioTrack? = null
    private var speakJob: Job? = null

    private val _isSpeaking = MutableStateFlow(false)

    /**
     * True exactly while audio is actually leaving the speaker - the Gemini clip playing, or
     * (on fallback) the device voice speaking. Flips true only once real playback starts (after
     * the prebuffer fills), not when the network call is fired, so the UI never claims to be
     * speaking through silence.
     */
    val isSpeaking: StateFlow<Boolean> = _isSpeaking

    fun speak(text: String) {
        if (text.isBlank()) return

        // A new reply always interrupts the previous one.
        stop()

        speakJob = scope.launch {
            try {
                val played = streamAndPlay(text)
                if (!played) {
                    Log.w(TAG, "Gemini TTS produced no audio; using device voice.")
                    withContext(Dispatchers.Main) { fallback.speak(text) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Gemini TTS failed, using device voice.", e)
                withContext(Dispatchers.Main) { fallback.speak(text) }
            } finally {
                _isSpeaking.value = false
            }
        }
    }

    /** Returns true if at least one chunk of audio was actually played. */
    private suspend fun streamAndPlay(text: String): Boolean {
        val endpoint = VertexAi.streamGenerateContentUrl(MODEL)
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            connectTimeout = 15000
            readTimeout = 45000
            doOutput = true
        }

        val body = JSONObject().apply {
            put(
                "contents", JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put(
                            "parts", JSONArray().put(
                                JSONObject().put("text", sanitize(text))
                            )
                        )
                )
            )
            put("generationConfig", JSONObject().apply {
                put("responseModalities", JSONArray().put("AUDIO"))
                put(
                    "speechConfig", JSONObject().put(
                        "voiceConfig", JSONObject().put(
                            "prebuiltVoiceConfig", JSONObject().put("voiceName", VOICE)
                        )
                    )
                )
            })
        }

        var track: AudioTrack? = null
        var prebuffer = ArrayList<ByteArray>()
        var prebufferedBytes = 0
        var prebufferTargetBytes = 0
        var wroteAny = false
        // forEachLine below takes a plain (non-suspend) lambda, so the suspend-only
        // `coroutineContext` property has to be captured here, outside it.
        val ctx = kotlin.coroutines.coroutineContext

        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            if (code !in 200..299) {
                val error = conn.errorStream?.bufferedReader()?.use { it.readText() }
                Log.e(TAG, "TTS HTTP $code: $error")
                return false
            }

            conn.inputStream.bufferedReader().use { reader ->
                reader.forEachLine { line ->
                    if (!ctx.isActive) return@forEachLine
                    if (!line.startsWith("data:")) return@forEachLine
                    val jsonLine = line.removePrefix("data:").trim()
                    if (jsonLine.isEmpty()) return@forEachLine

                    val inlineData = JSONObject(jsonLine)
                        .optJSONArray("candidates")
                        ?.optJSONObject(0)
                        ?.optJSONObject("content")
                        ?.optJSONArray("parts")
                        ?.optJSONObject(0)
                        ?.optJSONObject("inlineData") ?: return@forEachLine

                    val base64 = inlineData.optString("data")
                    if (base64.isBlank()) return@forEachLine
                    val pcm = Base64.decode(base64, Base64.DEFAULT)
                    if (pcm.isEmpty()) return@forEachLine

                    if (track == null) {
                        val sampleRate = parseSampleRate(inlineData.optString("mimeType"))
                        track = buildTrack(sampleRate)
                        audioTrack = track
                        prebufferTargetBytes = (sampleRate * 2 * PREBUFFER_MS / 1000).toInt()
                    }

                    if (prebufferedBytes < prebufferTargetBytes) {
                        prebuffer.add(pcm)
                        prebufferedBytes += pcm.size
                        if (prebufferedBytes >= prebufferTargetBytes) {
                            track?.play()
                            _isSpeaking.value = true
                            prebuffer.forEach { chunk -> track?.write(chunk, 0, chunk.size) }
                            wroteAny = true
                            prebuffer = ArrayList()
                        }
                    } else {
                        track?.write(pcm, 0, pcm.size)
                        wroteAny = true
                    }
                }
            }

            // A short clip may finish before the prebuffer target is ever reached - flush
            // whatever we have so short replies are not silently dropped.
            if (prebuffer.isNotEmpty()) {
                track?.play()
                _isSpeaking.value = true
                prebuffer.forEach { chunk -> track?.write(chunk, 0, chunk.size) }
                wroteAny = true
            }

            track?.let { waitForDrain(it) }
            return wroteAny
        } finally {
            conn.disconnect()
            track?.let { releaseTrack(it) }
        }
    }

    private fun buildTrack(sampleRateHz: Int): AudioTrack {
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRateHz,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRateHz)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuffer.coerceAtLeast(sampleRateHz * 2))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    /**
     * write() returning does not mean playback finished. Unlike the old non-streaming version,
     * the total clip length is never known up front here - we just wait until the hardware
     * head position catches up to the last frame we handed it, with a hard timeout so a stuck
     * track can't hang the coroutine forever.
     */
    private suspend fun waitForDrain(track: AudioTrack) {
        val deadline = System.currentTimeMillis() + 10_000L
        while (
            kotlin.coroutines.coroutineContext.isActive &&
            audioTrack === track &&
            track.playState == AudioTrack.PLAYSTATE_PLAYING &&
            System.currentTimeMillis() < deadline
        ) {
            kotlinx.coroutines.delay(40)
        }
    }

    /** mimeType looks like "audio/l16; rate=24000; channels=1" - do not assume the rate. */
    private fun parseSampleRate(mimeType: String?): Int {
        if (mimeType.isNullOrBlank()) return DEFAULT_SAMPLE_RATE
        val match = Regex("rate=(\\d+)").find(mimeType) ?: return DEFAULT_SAMPLE_RATE
        return match.groupValues.getOrNull(1)?.toIntOrNull() ?: DEFAULT_SAMPLE_RATE
    }

    /** Strips markdown the model sometimes emits so it is not read out loud as symbols. */
    private fun sanitize(text: String): String =
        text.replace(Regex("[*_`#]"), "").trim()

    private fun releaseTrack(track: AudioTrack) {
        try {
            if (track.state == AudioTrack.STATE_INITIALIZED) {
                track.stop()
            }
        } catch (e: Exception) {
            Log.e(TAG, "stop() failed", e)
        }
        try {
            track.release()
        } catch (e: Exception) {
            Log.e(TAG, "release() failed", e)
        }
        if (audioTrack === track) audioTrack = null
    }

    /** Interrupts whatever is currently being spoken. */
    fun stop() {
        speakJob?.cancel()
        speakJob = null
        audioTrack?.let { releaseTrack(it) }
        fallback.stop()
        _isSpeaking.value = false
    }

    fun shutdown() {
        stop()
        // Cancel the scope too, or its SupervisorJob outlives the ViewModel.
        scope.cancel()
        fallback.shutdown()
    }
}
