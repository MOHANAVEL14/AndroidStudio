package com.example.sossmsapp

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.location.*

class MonitorActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Simple programmatic layout
        statusText = TextView(this).apply {
            textSize = 18f
            setPadding(50, 50, 50, 50)
            text = "Initializing GPS Monitoring..."
        }
        setContentView(statusText)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Define how we want to receive updates
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000).build()

        locationCallback = object : LocationCallback() {
            @SuppressLint("SetTextI18n")
            override fun onLocationResult(locationResult: LocationResult) {
                val loc = locationResult.lastLocation
                if (loc != null) {
                    statusText.text = """
                        🛰️ GPS Status: ACTIVE
                        
                        Latitude: ${loc.latitude}
                        Longitude: ${loc.longitude}
                        Accuracy: ${loc.accuracy} meters
                        
                        SOS Link Preview:
                        https://maps.google.com/maps?q=${loc.latitude},${loc.longitude}
                        
                        Keep this screen open for 10 seconds to 
                        calibrate the SOS trigger.
                    """.trimIndent()
                }
            }
        }

        startTracking()
    }

    @SuppressLint("MissingPermission")
    private fun startTracking() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000).build()
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}