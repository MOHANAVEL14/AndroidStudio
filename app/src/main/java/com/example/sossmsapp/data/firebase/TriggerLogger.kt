package com.example.sossmsapp.data.firebase

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

object TriggerLogger {

    private const val TAG = "TriggerLogger"

    /**
     * Logs an emergency trigger to Firestore under the current user's document.
     */
    fun logTrigger(
        context: Context,
        triggerType: String,
        latitude: Double? = 0.0,
        longitude: Double? = 0.0,
        riskScore: Int? = null
    ) {
        Log.d(TAG, "LOG_TRIGGER_LOGGING_STARTED: Type: $triggerType")

        val auth = FirebaseAuth.getInstance()
        val db = FirebaseFirestore.getInstance()
        val userId = auth.currentUser?.uid

        if (userId == null) {
            Log.e(TAG, "LOG_FIREBASE_WRITE_FAILED: No authenticated user found.")
            return
        }

        // Gather device metadata
        val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

        // Prepare data map
        val triggerData = hashMapOf(
            "type" to triggerType,
            "timestamp" to FieldValue.serverTimestamp(),
            "latitude" to (latitude ?: 0.0),
            "longitude" to (longitude ?: 0.0),
            "riskScore" to riskScore,
            "deviceModel" to deviceModel,
            "batteryLevel" to batteryLevel
        )

        // Path: users/{userId}/triggers/{autoId}
        db.collection("users")
            .document(userId)
            .collection("triggers")
            .add(triggerData)
            .addOnSuccessListener {
                Log.d(TAG, "LOG_FIREBASE_WRITE_SUCCESS: Trigger ID: ${it.id}")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "LOG_FIREBASE_WRITE_FAILED: ${e.message}")
            }
    }
}