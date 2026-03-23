package com.example.sossmsapp

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit // For cleaner SharedPreferences code
import com.google.android.material.materialswitch.MaterialSwitch
import com.example.sossmsapp.auth.SessionManager
import com.example.sossmsapp.iu.auth.RegisterActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Using AppConstants.PREFS_NAME to match your ShakeService
        prefs = getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)
        sessionManager = SessionManager(this)

        val rgSensitivity = findViewById<RadioGroup>(R.id.rgSensitivity)
        val swVolume = findViewById<MaterialSwitch>(R.id.swVolume)
        val swLocation = findViewById<MaterialSwitch>(R.id.swLocation)
        val swTorch = findViewById<MaterialSwitch>(R.id.swTorch)
        val swAudio = findViewById<MaterialSwitch>(R.id.swAudio)
        val swPhoto = findViewById<MaterialSwitch>(R.id.swPhoto)
        val btnLogout = findViewById<MaterialButton>(R.id.btnLogout)

        // Load Settings
        loadSettings(swVolume, swLocation, swTorch, swAudio, swPhoto)

        // Sensitivity
        rgSensitivity.setOnCheckedChangeListener { _, checkedId ->
            val value = when (checkedId) {
                R.id.rbLow -> "LOW"
                R.id.rbHigh -> "HIGH"
                else -> "MEDIUM"
            }
            prefs.edit { putString("SHAKE_SENSITIVITY", value) }
            notifyService()
        }

        // Switches
        swVolume.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean("ENABLE_VOLUME_TRIGGER", isChecked) }
            notifyService()
        }

        swLocation.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean("ENABLE_LOCATION", isChecked) }
        }

        swTorch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean("ENABLE_TORCH", isChecked) }
        }

        swAudio.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean("ENABLE_AUDIO", isChecked) }
        }

        swPhoto.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean("ENABLE_PHOTO", isChecked) }
        }

        btnLogout.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            sessionManager.setLoggedIn(false)
            startActivity(Intent(this, RegisterActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
        }
    }

    private fun loadSettings(vol: MaterialSwitch, loc: MaterialSwitch, torch: MaterialSwitch, audio: MaterialSwitch, photo: MaterialSwitch) {
        val sensitivity = prefs.getString("SHAKE_SENSITIVITY", "MEDIUM")
        when (sensitivity) {
            "LOW" -> findViewById<RadioButton>(R.id.rbLow).isChecked = true
            "HIGH" -> findViewById<RadioButton>(R.id.rbHigh).isChecked = true
            else -> findViewById<RadioButton>(R.id.rbMedium).isChecked = true
        }

        vol.isChecked = prefs.getBoolean("ENABLE_VOLUME_TRIGGER", false)
        loc.isChecked = prefs.getBoolean("ENABLE_LOCATION", false)
        torch.isChecked = prefs.getBoolean("ENABLE_TORCH", false)
        audio.isChecked = prefs.getBoolean("ENABLE_AUDIO", false)
        photo.isChecked = prefs.getBoolean("ENABLE_PHOTO", false)

        // Prevent that measurement crash
        listOf(vol, loc, torch, audio, photo).forEach { it.text = "" }
    }

    private fun notifyService() {
        startService(Intent(this, ShakeService::class.java))
    }
}