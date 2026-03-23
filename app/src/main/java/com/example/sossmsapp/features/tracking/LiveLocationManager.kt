package com.example.sossmsapp.features.tracking

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID

class LiveLocationManager(private val context: Context) {
    private val TAG = "LiveLocationManager"
    
    private var fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
    private var locationCallback: LocationCallback? = null
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    private var currentSessionId: String? = null
    private var isTracking = false
    
    // Offline caching
    private val cachedLocations = mutableListOf<HashMap<String, Any>>()
    
    // 30 min timer
    private val TRACKING_DURATION_MS = 30L * 60L * 1000L
    private val handler = Handler(Looper.getMainLooper())
    private val stopRunnable = Runnable { stopTracking() }
    
    @SuppressLint("MissingPermission")
    fun startTracking(): String {
        if (isTracking && currentSessionId != null) return currentSessionId!!
        
        currentSessionId = UUID.randomUUID().toString()
        isTracking = true
        cachedLocations.clear()
        
        // Ensure UI/Firestore setup for active alert
        val userId = auth.currentUser?.uid ?: "anonymous"
        val alertData = hashMapOf(
            "user_id" to userId,
            "start_time" to System.currentTimeMillis(),
            "is_active" to true
        )
        db.collection("active_alerts").document(currentSessionId!!).set(alertData)
        
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 15000)
            .setMinUpdateDistanceMeters(0f)
            .build()
            
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation ?: return
                pushLocationToFirebase(location)
            }
        }
        
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback!!, Looper.getMainLooper())
        
        // Auto stop after 30 mins
        handler.postDelayed(stopRunnable, TRACKING_DURATION_MS)
        return currentSessionId!!
    }
    
    private fun pushLocationToFirebase(location: Location) {
        val sessionId = currentSessionId ?: return
        
        val locData = hashMapOf<String, Any>(
            "lat" to location.latitude,
            "lng" to location.longitude,
            "accuracy" to location.accuracy,
            "timestamp" to System.currentTimeMillis()
        )
        
        // Add to cache
        cachedLocations.add(locData)
        
        // Attempt upload using batch
        val batch = db.batch()
        val locationsRef = db.collection("active_alerts").document(sessionId).collection("locations")
        
        val currentCache = cachedLocations.toList() // Copy to avoid concurrent modification
        
        for (loc in currentCache) {
            val docRef = locationsRef.document() // Auto-generate ID
            batch.set(docRef, loc)
        }
        
        batch.commit()
            .addOnSuccessListener {
                Log.d(TAG, "Successfully uploaded ${currentCache.size} locations.")
                cachedLocations.removeAll(currentCache)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to upload locations (cached size: ${cachedLocations.size}): ${e.message}")
            }
    }
    
    fun stopTracking() {
        if (!isTracking) return
        
        Log.d(TAG, "Stopping live location tracking")
        isTracking = false
        handler.removeCallbacks(stopRunnable)
        
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }
        locationCallback = null
        
        currentSessionId?.let { sessionId ->
            db.collection("active_alerts").document(sessionId)
                .update("is_active", false)
                .addOnSuccessListener { Log.d(TAG, "Marked alert as inactive") }
        }
        currentSessionId = null
    }

    fun isCurrentlyTracking(): Boolean = isTracking
}
