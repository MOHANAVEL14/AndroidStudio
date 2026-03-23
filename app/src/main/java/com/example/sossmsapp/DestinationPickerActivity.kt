package com.example.sossmsapp

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import com.google.android.material.button.MaterialButton

class DestinationPickerActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private var selectedLatLng: LatLng? = null
    private lateinit var btnConfirm: MaterialButton
    private lateinit var tvSelectedPlace: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_destination_picker)

        btnConfirm = findViewById(R.id.btnConfirmDest)
        tvSelectedPlace = findViewById(R.id.tvSelectedPlace)

        // Initialize Places SDK
        val apiKey = getString(R.string.google_maps_key)
        if (!Places.isInitialized()) {
            Places.initialize(applicationContext, apiKey)
        }

        // Setup the Autocomplete search fragment
        setupPlacesAutocomplete()

        // Load and render the map
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        btnConfirm.setOnClickListener {
            selectedLatLng?.let { latLng ->
                Log.d("MAP_PICKER", "Confirming Lat: ${latLng.latitude}, Lng: ${latLng.longitude}")
                saveToPrefs(latLng)
                Toast.makeText(this, "Destination Saved!", Toast.LENGTH_SHORT).show()
                finish()
            } ?: run {
                Toast.makeText(this, "Search for a destination above", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupPlacesAutocomplete() {
        val autocompleteFragment = AutocompleteSupportFragment.newInstance()

        supportFragmentManager.beginTransaction()
            .replace(R.id.autocompleteFragment, autocompleteFragment)
            .commit()

        // Specify which data types to return
        autocompleteFragment.setPlaceFields(
            listOf(Place.Field.ID, Place.Field.DISPLAY_NAME, Place.Field.FORMATTED_ADDRESS, Place.Field.LOCATION)
        )
        autocompleteFragment.setHint("Search for a place...")

        autocompleteFragment.setOnPlaceSelectedListener(object : PlaceSelectionListener {
            override fun onPlaceSelected(place: Place) {
                val latLng = place.location
                if (latLng != null) {
                    val name = place.displayName ?: place.formattedAddress ?: "Selected Place"
                    Log.d("MAP_PICKER", "Place selected: $name at $latLng")

                    // Update the label
                    tvSelectedPlace.text = "📍 $name"
                    tvSelectedPlace.visibility = View.VISIBLE

                    // Update marker and camera on the map
                    updateMarker(LatLng(latLng.latitude, latLng.longitude))

                    // Zoom into the selected location
                    if (::mMap.isInitialized) {
                        mMap.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(LatLng(latLng.latitude, latLng.longitude), 15f)
                        )
                    }
                } else {
                    Toast.makeText(this@DestinationPickerActivity, "Could not get coordinates for this place.", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onError(status: com.google.android.gms.common.api.Status) {
                Log.e("MAP_PICKER", "Autocomplete error: ${status.statusMessage}")
                Toast.makeText(this@DestinationPickerActivity, "Search error: ${status.statusMessage}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    @SuppressLint("MissingPermission")
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        Log.d("MAP_PICKER", "Map Ready")

        mMap.isMyLocationEnabled = true
        mMap.uiSettings.isZoomControlsEnabled = true
        mMap.uiSettings.isMyLocationButtonEnabled = true

        // Load existing destination if available and show on map
        val prefs = getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)
        val savedLat = prefs.getString(AppConstants.KEY_DEST_LAT, null)?.toDoubleOrNull()
        val savedLng = prefs.getString(AppConstants.KEY_DEST_LNG, null)?.toDoubleOrNull()

        if (savedLat != null && savedLng != null) {
            val savedLoc = LatLng(savedLat, savedLng)
            updateMarker(savedLoc)
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(savedLoc, 15f))
            tvSelectedPlace.text = "📍 Previously saved destination"
            tvSelectedPlace.visibility = View.VISIBLE
        }

        // Allow tap-on-map as a fallback (map tap still works as backup)
        mMap.setOnMapClickListener { latLng ->
            tvSelectedPlace.text = "📍 Custom pin: ${String.format("%.5f", latLng.latitude)}, ${String.format("%.5f", latLng.longitude)}"
            tvSelectedPlace.visibility = View.VISIBLE
            updateMarker(latLng)
        }
    }

    private fun updateMarker(latLng: LatLng) {
        selectedLatLng = latLng
        if (::mMap.isInitialized) {
            mMap.clear()
            mMap.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title("Destination")
            )
        }
        btnConfirm.visibility = View.VISIBLE
    }

    private fun saveToPrefs(latLng: LatLng) {
        val prefs = getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)
        val success = prefs.edit()
            .putString(AppConstants.KEY_DEST_LAT, latLng.latitude.toString())
            .putString(AppConstants.KEY_DEST_LNG, latLng.longitude.toString())
            .commit()
        Log.d("MAP_PICKER", "Save Success: $success | Lat: ${latLng.latitude} Lng: ${latLng.longitude}")
    }
}