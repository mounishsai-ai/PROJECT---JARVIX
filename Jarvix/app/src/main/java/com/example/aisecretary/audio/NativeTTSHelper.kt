package com.example.aisecretary.audio

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale

class NativeTTSHelper(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var isReady = false
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Fires when a spoken utterance genuinely finishes or errors - not a fixed delay guess. */
    var onDone: (() -> Unit)? = null

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isReady = true
            setupPremiumVoice()
        } else {
            Log.e("NativeTTSHelper", "TTS Initialization failed!")
        }
    }

    private fun setupPremiumVoice() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                mainHandler.post { onDone?.invoke() }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                mainHandler.post { onDone?.invoke() }
            }
        })
        tts?.let { textToSpeech ->
            val allVoices = textToSpeech.voices ?: return
            
            // Force English matching (handles cases where language is "en" or "eng")
            val englishVoices = allVoices.filter { it.locale.language.startsWith("en", ignoreCase = true) }
            
            val premiumVoice = englishVoices.find { it.isNetworkConnectionRequired && it.locale.country == "GB" }
                ?: englishVoices.find { it.isNetworkConnectionRequired && it.locale.country == "US" }
                ?: englishVoices.find { it.isNetworkConnectionRequired }
                ?: englishVoices.firstOrNull()

            if (premiumVoice != null) {
                Log.d("NativeTTSHelper", "Selected Voice: ${premiumVoice.name}")
                textToSpeech.language = premiumVoice.locale
                textToSpeech.voice = premiumVoice
            } else {
                Log.d("NativeTTSHelper", "No suitable English voice found, forcing UK Locale.")
                textToSpeech.language = Locale.UK
            }
            
            // Reduce speed to 1.15f as requested
            textToSpeech.setSpeechRate(1.15f)
        }
    }

    fun speak(text: String) {
        if (isReady && text.isNotBlank()) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis_tts_id")
        }
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.e("NativeTTSHelper", "stop() failed", e)
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
