package com.example.aisecretary.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import com.example.aisecretary.R

/**
 * Quiet looping HUD atmosphere bed.
 *
 * Deliberately muted (see BED_VOLUME) - this sits UNDER Jarvix's voice and must never compete
 * with it.
 *
 * IMPORTANT: this has to be silenced while the microphone is recording. The phone's speaker
 * feeds straight back into the mic, so a bed playing during capture would be baked into the
 * audio uploaded to Gemini and degrade what it hears. `setActive(...)` handles that - it is
 * not merely cosmetic.
 */
class AmbientAudioPlayer(private val context: Context) {

    companion object {
        private const val TAG = "AmbientAudioPlayer"

        /** Low on purpose: atmosphere, not music. */
        private const val BED_VOLUME = 0.18f
    }

    private var player: MediaPlayer? = null
    private var enabled = true

    /** True only when nothing else needs the audio path (not recording, not speaking). */
    private var pathClear = false

    fun setEnabled(value: Boolean) {
        enabled = value
        applyState()
    }

    fun isEnabled(): Boolean = enabled

    /**
     * @param clear true when the mic is idle and Jarvix is not speaking.
     */
    fun setActive(clear: Boolean) {
        pathClear = clear
        applyState()
    }

    private fun applyState() {
        if (enabled && pathClear) start() else pause()
    }

    private fun start() {
        try {
            if (player == null) {
                player = MediaPlayer.create(context, R.raw.ambient_loop)?.apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    isLooping = true
                    setVolume(BED_VOLUME, BED_VOLUME)
                }
            }
            player?.let { if (!it.isPlaying) it.start() }
        } catch (e: Exception) {
            Log.e(TAG, "Could not start ambient bed", e)
        }
    }

    private fun pause() {
        try {
            player?.let { if (it.isPlaying) it.pause() }
        } catch (e: Exception) {
            Log.e(TAG, "Could not pause ambient bed", e)
        }
    }

    fun release() {
        try {
            player?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Could not release ambient bed", e)
        }
        player = null
        pathClear = false
    }
}
