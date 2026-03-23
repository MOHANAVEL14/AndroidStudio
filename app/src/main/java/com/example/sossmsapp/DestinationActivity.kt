package com.example.sossmsapp

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices

class DestinationActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // Using the Constants we defined in the previous step
    private val prefs by lazy {
        getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_destination)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val etLat = findViewById<EditText>(R.id.etLat)
        val etLng = findViewById<EditText>(R.id.etLng)
        val etName = findViewById<EditText>(R.id.etDestName)
        val btnSave = findViewById<Button>(R.id.btnSaveDest)
        val btnCurrent = findViewById<Button>(R.id.btnUseCurrent)

        // Option 1: Manual Input
        btnSave.setOnClickListener {
            val lat = etLat.text.toString()
            val lng = etLng.text.toString()
            val name = etName.text.toString()

            if (lat.isNotEmpty() && lng.isNotEmpty()) {
                saveDestination(lat, lng, name)
                finish()
            } else {
                Toast.makeText(this, "Please enter coordinates", Toast.LENGTH_SHORT).show()
            }
        }

        // Option 2: Use Current Location (Best for testing Phase 1)
        btnCurrent.setOnClickListener {
            fetchAndSaveCurrentLocation()
        }
    }

    @SuppressLint("MissingPermission")
    private fun fetchAndSaveCurrentLocation() {
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                saveDestination(
                    location.latitude.toString(),
                    location.longitude.toString(),
                    "Current Location"
                )
                Toast.makeText(this, "Current Location Saved as Destination", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this, "Could not get location. Is GPS on?", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveDestination(lat: String, lng: String, name: String) {
        prefs.edit().apply {
            putString(AppConstants.KEY_DEST_LAT, lat)
            putString(AppConstants.KEY_DEST_LNG, lng)
            putString(AppConstants.KEY_DEST_NAME, name.ifEmpty { "Target" })
            apply()
        }
    }
}