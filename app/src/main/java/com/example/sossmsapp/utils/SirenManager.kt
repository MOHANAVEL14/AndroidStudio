package com.example.sossmsapp.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Log
import com.example.sossmsapp.R

object SirenManager {

    private const val TAG = "SirenManager"
    private var mediaPlayer: MediaPlayer? = null

    /**
     * Starts the siren sound at maximum volume and loops it.
     */
    fun startSiren(context: Context) {
        if (mediaPlayer != null && mediaPlayer!!.isPlaying) return // Already playing

        try {
            mediaPlayer = MediaPlayer.create(context, R.raw.siren).apply {
                isLooping = true
                // Ensure it plays on the Alarm/Notification stream for maximum loudness
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setVolume(1.0f, 1.0f) // Max volume
                start()
            }
            Log.d(TAG, "LOG_SIREN_STARTED: Siren is now playing at max volume.")
        } catch (e: Exception) {
            Log.e(TAG, "LOG_ERROR: Failed to start siren: ${e.message}")
        }
    }

    /**
     * Stops the siren and releases resources.
     */
    fun stopSiren() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
                mediaPlayer = null
                Log.d(TAG, "LOG_SIREN_STOPPED: Siren has been silenced.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "LOG_ERROR: Failed to stop siren: ${e.message}")
        }
    }
}