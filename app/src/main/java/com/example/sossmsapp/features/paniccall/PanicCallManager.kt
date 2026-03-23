package com.example.sossmsapp.features.paniccall

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import com.example.sossmsapp.ContactManager
import com.example.sossmsapp.R // Ensure this points to your app's R file
import com.example.sossmsapp.data.firebase.TriggerLogger

class PanicCallManager(private val context: Context) {

    private val TAG = "PanicCallManager"
    private var mediaPlayer: MediaPlayer? = null

    fun startPanicCall() {
        Log.d(TAG, "LOG_PANIC_CALL_STARTED: Initializing panic sequence.")

        val contactManager = ContactManager(context)
        val contacts = contactManager.getContacts()

        if (contacts.isNotEmpty()) {
            val firstContact = contacts[0]
            val phoneNumber = firstContact.phoneNumber

            Log.d(TAG, "LOG_CONTACT_NUMBER_FOUND: Found ${firstContact.name} at $phoneNumber")
            TriggerLogger.logTrigger(context, "PANIC_CALL")

            // 1. Play the audio message
            playEmergencyMessage()

            // 2. Start the call
            makePhoneCall(phoneNumber)
        } else {
            Log.e(TAG, "LOG_ERROR: No emergency contacts found.")
        }
    }

    private fun playEmergencyMessage() {
        try {
            // Initialize MediaPlayer with the raw resource
            mediaPlayer = MediaPlayer.create(context, R.raw.panic_message)

            mediaPlayer?.setOnCompletionListener {
                Log.d(TAG, "LOG_AUDIO_PLAYED: Emergency message finished playing.")
                releaseMediaPlayer()
            }

            mediaPlayer?.start()
            Log.d(TAG, "LOG_AUDIO_STARTED: Playing pre-recorded message.")
        } catch (e: Exception) {
            Log.e(TAG, "LOG_ERROR: Could not play audio: ${e.message}")
        }
    }

    private fun makePhoneCall(number: String) {
        try {
            // Clean the number to remove any weird formatting
            val cleanedNumber = number.replace(" ", "").replace("-", "")

            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$cleanedNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            Log.d(TAG, "Dials_Target: Calling exactly $cleanedNumber")
            context.startActivity(intent)

        } catch (e: Exception) {
            Log.e(TAG, "LOG_ERROR: Failed to initiate call: ${e.message}")
        }
    }

    private fun releaseMediaPlayer() {
        mediaPlayer?.release()
        mediaPlayer = null
    }
}