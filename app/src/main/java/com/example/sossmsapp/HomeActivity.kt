package com.example.sossmsapp

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.sossmsapp.ai.security.PinVerificationDialog
import androidx.appcompat.widget.SwitchCompat
import com.example.sossmsapp.features.paniccall.PanicCallManager // NEW: Import Manager
import com.google.android.material.button.MaterialButton // NEW: For Material 3 buttons

class HomeActivity : AppCompatActivity() {

    private lateinit var travelModeSwitch: SwitchCompat
    private lateinit var btnSetDestination: Button

    // NEW: Panic Call Manager instance
    private lateinit var panicCallManager: PanicCallManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        // NEW: Initialize the Panic Call Manager
        panicCallManager = PanicCallManager(this)
        val btnManualSOS = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnManualSOS)

// 2. Set the click listener to handle the emergency sequence
        btnManualSOS.setOnClickListener {
            android.util.Log.d("HomeActivity", "LOG_MANUAL_SOS_CLICKED: User initiated emergency sequence")
            handleManualSOS()
        }

        // UI Components
        val safeModeSwitch = findViewById<SwitchCompat>(R.id.safeModeSwitch)
        travelModeSwitch = findViewById<SwitchCompat>(R.id.travelModeSwitch)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)
        val btnSettings = findViewById<Button>(R.id.btnSettings)
        btnSetDestination = findViewById<Button>(R.id.btnSetDestination)

        // NEW: Find and Setup Panic Call Button
        // Ensure android:id="@+id/btnPanicCall" exists in activity_home.xml
        val btnPanicCall = findViewById<MaterialButton>(R.id.btnPanicCall)
        btnPanicCall.setOnClickListener {
            // This triggers Step 4 logic: Audio message + Call
            panicCallManager.startPanicCall()
        }

        // SharedPreferences
        val sharedPref = getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)
        if (!sharedPref.contains("APP_CANCEL_PIN")) {
            sharedPref.edit().putString("APP_CANCEL_PIN", "1234").apply()
            android.util.Log.d("HomeActivity", "LOG_PIN_INITIALIZED: Default PIN 1234 set")
        }

        // --- SAFE MODE LOGIC ---
        val isSafeEnabled = sharedPref.getBoolean("SAFE_MODE_ENABLED", false)
        safeModeSwitch.isChecked = isSafeEnabled
        updateStatusText(tvStatus, isSafeEnabled)

        safeModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            sharedPref.edit().putBoolean("SAFE_MODE_ENABLED", isChecked).apply()
            updateStatusText(tvStatus, isChecked)
            if (isChecked) startSosService() else stopSosService()
        }

        // --- TRAVEL MODE LOGIC (UPGRADED) ---
        val isTravelEnabled = sharedPref.getBoolean(AppConstants.KEY_TRAVEL_MODE_ENABLED, false)
        travelModeSwitch.isChecked = isTravelEnabled

        travelModeSwitch.setOnCheckedChangeListener { buttonView, isChecked ->
            val destLat = sharedPref.getString(AppConstants.KEY_DEST_LAT, null)
            val destLng = sharedPref.getString(AppConstants.KEY_DEST_LNG, null)

            if (isChecked && (destLat == null || destLng == null)) {
                buttonView.isChecked = false
                Toast.makeText(this, "Please set a destination on the map first", Toast.LENGTH_LONG).show()
                startActivity(Intent(this, DestinationPickerActivity::class.java))
                return@setOnCheckedChangeListener
            }

            sharedPref.edit().putBoolean(AppConstants.KEY_TRAVEL_MODE_ENABLED, isChecked).apply()

            val intent = Intent(this, ShakeService::class.java).apply {
                action = if (isChecked) "START_TRAVEL_MONITORING" else "STOP_TRAVEL_MONITORING"
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }

        // --- NAVIGATION ---
        btnSetDestination.setOnClickListener {
            startActivity(Intent(this, DestinationPickerActivity::class.java))
        }

        findViewById<Button>(R.id.btnManageGuardians).setOnClickListener {
            val intent = Intent(this, EmergencyContactActivity::class.java)
            intent.putExtra("IS_EDITING", true)
            startActivity(intent)
        }

        findViewById<Button>(R.id.btnMonitor).setOnClickListener {
            startActivity(Intent(this, MonitorActivity::class.java))
        }

        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // Initial Start check
        if (isSafeEnabled || isTravelEnabled) {
            startSosService()
        }
        intent?.let { checkPinTrigger(it) }
    }

    override fun onResume() {
        super.onResume()
        val sharedPref = getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)
        val destLat = sharedPref.getString(AppConstants.KEY_DEST_LAT, null)

        if (destLat != null) {
            btnSetDestination.text = "Destination Set (Change)"
        } else {
            btnSetDestination.text = "Set Destination"
        }
    }

    private fun updateStatusText(textView: TextView, isEnabled: Boolean) {
        if (isEnabled) {
            textView.text = "Safe Mode is ON. Monitoring in background"
            textView.setTextColor(resources.getColor(android.R.color.holo_green_dark))
        } else {
            textView.text = "Safe Mode is OFF"
            textView.setTextColor(resources.getColor(android.R.color.holo_red_dark))
        }
    }

    private fun startSosService() {
        val intent = Intent(this, ShakeService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopSosService() {
        val sharedPref = getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)
        val travelOn = sharedPref.getBoolean(AppConstants.KEY_TRAVEL_MODE_ENABLED, false)
        val safeOn = sharedPref.getBoolean("SAFE_MODE_ENABLED", false)

        if (!travelOn && !safeOn) {
            val intent = Intent(this, ShakeService::class.java)
            stopService(intent)
        }
    }
    private fun handleManualSOS() {
        // 1. Log the initial button click
        android.util.Log.d("HomeActivity", "LOG_MANUAL_SOS_CLICKED")

        // 2. Trigger the SMS Flow via ShakeService
        val smsIntent = Intent(this, ShakeService::class.java).apply {
            action = "ACTION_SEND_NOW"
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(smsIntent)
            } else {
                startService(smsIntent)
            }
            // 3. Log the SMS trigger
            android.util.Log.d("HomeActivity", "LOG_SMS_TRIGGERED: Sent to ShakeService")
        } catch (e: Exception) {
            android.util.Log.e("HomeActivity", "LOG_ERROR: SMS Trigger failed: ${e.message}")
        }

        // 4. Trigger the Panic Call via PanicCallManager
        try {
            panicCallManager.startPanicCall()
            // 5. Log the Call trigger
            android.util.Log.d("HomeActivity", "LOG_CALL_TRIGGERED: PanicCallManager initiated")
        } catch (e: Exception) {
            android.util.Log.e("HomeActivity", "LOG_ERROR: Panic Call failed: ${e.message}")
        }
    }
    // NEW: Function to check if we need to show the PIN Dialog
    // 1. Fixed onNewIntent
    override fun onNewIntent(intent: Intent) { // REMOVE the '?' here
        super.onNewIntent(intent)
        setIntent(intent)
        checkPinTrigger(intent)
    }

    // 2. Fixed checkPinTrigger to handle potential nulls safely
    private fun checkPinTrigger(intent: Intent?) {
        if (intent?.getBooleanExtra("TRIGGER_PIN_VERIFY", false) == true) {
            // Note: Using the package name 'ai.security' as seen in your project tree
            com.example.sossmsapp.ai.security.PinVerificationDialog.show(this) {
                android.util.Log.d("HomeActivity", "LOG_PIN_SUCCESS: Stopping all emergency alerts")
                stopSosService()
                com.example.sossmsapp.utils.SirenManager.stopSiren()
                Toast.makeText(this, "System Disarmed. You are safe.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}