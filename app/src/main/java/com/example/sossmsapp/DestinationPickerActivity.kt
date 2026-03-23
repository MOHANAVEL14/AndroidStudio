package com.example.sossmsapp

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions

class DestinationPickerActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private var selectedLatLng: LatLng? = null
    private lateinit var btnConfirm: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_destination_picker)

        // Ensure this ID matches your activity_destination_picker.xml exactly
        btnConfirm = findViewById(R.id.btnConfirmDest)

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        btnConfirm.setOnClickListener {
            selectedLatLng?.let { latLng ->
                Log.d("MAP_PICKER", "Confirming Lat: ${latLng.latitude}, Lng: ${latLng.longitude}")
                saveToPrefs(latLng)

                // Show feedback so user knows it worked
                Toast.makeText(this, "Destination Saved", Toast.LENGTH_SHORT).show()

                // Finish with a slight delay or directly
                finish()
            } ?: run {
                Toast.makeText(this, "Please tap the map to select a destination", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        Log.d("MAP_PICKER", "Map Ready")

        mMap.isMyLocationEnabled = true
        mMap.uiSettings.isZoomControlsEnabled = true
        mMap.uiSettings.isMyLocationButtonEnabled = true

        // 1. Load existing destination if available
        val prefs = getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)
        val savedLat = prefs.getString(AppConstants.KEY_DEST_LAT, null)?.toDoubleOrNull()
        val savedLng = prefs.getString(AppConstants.KEY_DEST_LNG, null)?.toDoubleOrNull()

        if (savedLat != null && savedLng != null) {
            val savedLoc = LatLng(savedLat, savedLng)
            updateMarker(savedLoc)
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(savedLoc, 15f))
        }

        // 2. Map Click Listener
        mMap.setOnMapClickListener { latLng ->
            updateMarker(latLng)
        }
    }

    private fun updateMarker(latLng: LatLng) {
        selectedLatLng = latLng
        mMap.clear()
        mMap.addMarker(MarkerOptions()
            .position(latLng)
            .title("Selected Destination"))

        // Ensure button becomes visible only after a selection
        btnConfirm.visibility = View.VISIBLE
    }

    private fun saveToPrefs(latLng: LatLng) {
        val prefs = getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)
        // Use commit() instead of apply() to ensure data is written immediately
        // before the activity finishes and control returns to HomeActivity
        val success = prefs.edit()
            .putString(AppConstants.KEY_DEST_LAT, latLng.latitude.toString())
            .putString(AppConstants.KEY_DEST_LNG, latLng.longitude.toString())
            .commit()

        Log.d("MAP_PICKER", "Save Success: $success")
    }
}