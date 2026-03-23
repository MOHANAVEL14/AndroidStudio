package com.example.sossmsapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

// Note: Removed SensorEventListener from the Activity because the Service handles it now
class MainActivity : AppCompatActivity() {

    private lateinit var etPhoneNumber: EditText
    private lateinit var btnStartService: Button
    private lateinit var btnStopService: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etPhoneNumber = findViewById(R.id.etPhoneNumber)
        btnStartService = findViewById(R.id.btnSendSms) // Using your existing button ID

        // logic to start the background protection
        btnStartService.setOnClickListener {
            startSosService()
        }
    }

    private fun startSosService() {
        val phoneNumber = etPhoneNumber.text.toString().trim()

        if (phoneNumber.length < 10) {
            Toast.makeText(this, "Enter a valid 10-digit number first", Toast.LENGTH_SHORT).show()
            return
        }

        // List of permissions needed
        val permissions = mutableListOf(Manifest.permission.SEND_SMS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Check if permissions are granted
        val needsPermission = permissions.any {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needsPermission) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 101)
        } else {
            launchService(phoneNumber)
        }
    }

    private fun launchService(phoneNumber: String) {
        val intent = Intent(this, ShakeService::class.java)
        intent.putExtra("PHONE_NUMBER", phoneNumber)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        Toast.makeText(this, "SOS Protection Started", Toast.LENGTH_SHORT).show()
        moveTaskToBack(true) // Move app to background
    }

    // Call this if you add a stop button to your XML
    private fun stopSosService() {
        stopService(Intent(this, ShakeService::class.java))
        Toast.makeText(this, "SOS Protection Stopped", Toast.LENGTH_SHORT).show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launchService(etPhoneNumber.text.toString().trim())
            } else {
                Toast.makeText(this, "Permissions required for SOS to work", Toast.LENGTH_SHORT).show()
            }
        }
    }
}