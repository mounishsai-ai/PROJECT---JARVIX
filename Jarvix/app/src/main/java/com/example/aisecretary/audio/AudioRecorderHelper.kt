package com.example.aisecretary.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * Records the user's voice command.
 *
 * Format note: we deliberately record AAC in an ADTS container (a raw .aac stream), NOT an
 * MPEG-4 (.m4a) container. Gemini is told the upload is "audio/aac", and an .m4a file is an
 * MP4 container - the mismatch could make Gemini silently ignore the audio and answer from
 * the text prompt alone, which looks like a prompt bug rather than an audio bug.
 * Recording ADTS makes the file genuinely match the declared MIME type.
 */
class AudioRecorderHelper(private val context: Context) {

    companion object {
        const val MIME_TYPE = "audio/aac"
        private const val SAMPLE_RATE_HZ = 44_100
        private const val BIT_RATE = 128_000
    }

    private var recorder: MediaRecorder? = null
    private var audioFile: File? = null

    fun startRecording(): File? {
        // Clear any previous capture so the cache does not grow unbounded.
        discardCurrentFile()

        val file = File(context.cacheDir, "jarvix_command_${System.currentTimeMillis()}.aac")
        audioFile = file

        val newRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        return try {
            newRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioChannels(1)
                setAudioSamplingRate(SAMPLE_RATE_HZ)
                setAudioEncodingBitRate(BIT_RATE)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = newRecorder
            file
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                newRecorder.release()
            } catch (releaseError: Exception) {
                releaseError.printStackTrace()
            }
            recorder = null
            audioFile = null
            null
        }
    }

    fun stopRecording() {
        val active = recorder ?: return
        try {
            // stop() throws if the recording was too short to produce any frames.
            active.stop()
        } catch (e: Exception) {
            e.printStackTrace()
            discardCurrentFile()
        } finally {
            try {
                active.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            recorder = null
        }
    }

    fun getAudioFile(): File? = audioFile?.takeIf { it.exists() && it.length() > 0 }

    /**
     * Real input level from the mic, 0..32767, straight from MediaRecorder.
     * Used to drive the reactor's glow while listening - it is genuine amplitude,
     * not a simulated animation.
     */
    fun getMaxAmplitude(): Int =
        try {
            recorder?.maxAmplitude ?: 0
        } catch (e: Exception) {
            0
        }

    /** Deletes the captured file once it has been sent, so audio does not linger on disk. */
    fun discardCurrentFile() {
        try {
            audioFile?.takeIf { it.exists() }?.delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        audioFile = null
    }
}
