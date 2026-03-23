package com.example.sossmsapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class SOSReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return

        when (intent.action) {
            // REMOVED: ACTION_SAFE (This is now handled directly in ShakeService for better security)

            "ACTION_SEND_NOW" -> {
                Log.d("SOS_APP", "Receiver: Sending SOS now")
                val serviceIntent = Intent(context, ShakeService::class.java).apply {
                    action = "ACTION_SEND_NOW"
                }
                context.startService(serviceIntent)
            }
        }
    }
}