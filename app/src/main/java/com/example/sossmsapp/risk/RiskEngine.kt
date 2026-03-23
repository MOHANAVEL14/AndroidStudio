package com.example.sossmsapp.risk

import android.location.Location
import android.util.Log

class RiskEngine {

    // Movement History
    private var previousDistance: Float = -1f
    private var lastLocation: Location? = null
    private var increasingDistanceCount: Int = 0

    // Stop & Stagnation State
    private var stopStartTime: Long = 0
    private var isCurrentlyStopped: Boolean = false
    private var stopLocation: Location? = null
    private var stagnationStartTime: Long = 0
    private var baselineStagnationLocation: Location? = null

    // --- SCORING WEIGHTS ---
    private val WEIGHT_WRONG_DIRECTION = 15
    private val WEIGHT_SUDDEN_STOP = 40
    private val WEIGHT_STAGNATION = 30
    private val RISK_THRESHOLD_TOTAL = 50 // Your testing threshold

    // Thresholds
    private val JITTER_THRESHOLD = 7.0f
    private val SPEED_STOP_THRESHOLD = 0.28f // ~1.0 km/h
    private val STOP_TIME_THRESHOLD_MS = 10 * 1000 // 10 seconds for testing
    private val STAGNATION_TIME_THRESHOLD_MS = 3 * 60 * 1000

    fun evaluateRisk(currentLocation: Location, destLat: Double, destLng: Double): Int {
        val destLocation = Location("dest").apply {
            latitude = destLat
            longitude = destLng
        }

        val currentDistance = currentLocation.distanceTo(destLocation)
        var currentRiskScore = 0

        // --- MANUAL SPEED CALCULATION (Fallback for jitter/indoor) ---
        val calculatedSpeed = if (currentLocation.hasSpeed()) {
            currentLocation.speed
        } else if (lastLocation != null) {
            val distMoved = currentLocation.distanceTo(lastLocation!!)
            val timeDiffSecs = (currentLocation.time - lastLocation!!.time) / 1000f
            if (timeDiffSecs > 0.1f) distMoved / timeDiffSecs else 0.0f
        } else {
            0.0f
        }

        // 1. SUDDEN STOP CHECK
        if (calculatedSpeed < SPEED_STOP_THRESHOLD) {
            if (!isCurrentlyStopped) {
                stopStartTime = System.currentTimeMillis()
                stopLocation = currentLocation
                isCurrentlyStopped = true
                Log.d("RiskEngine", "Sudden stop detected. Starting timer...")
            } else {
                val durationStopped = System.currentTimeMillis() - stopStartTime
                if (durationStopped >= STOP_TIME_THRESHOLD_MS) {
                    currentRiskScore += WEIGHT_SUDDEN_STOP
                    Log.w("RiskEngine", "[+40] Point Added: Sudden Stop threshold reached")
                }
            }
        } else {
            // Check if we actually moved away from the stop point
            val distanceFromStop = stopLocation?.distanceTo(currentLocation) ?: 0f
            if (distanceFromStop > JITTER_THRESHOLD) {
                if (isCurrentlyStopped) {
                    Log.d("RiskEngine", "Movement resumed (Moved ${distanceFromStop.toInt()}m).")
                }
                isCurrentlyStopped = false
                stopLocation = null
            }
        }

        // 2. STAGNATION CHECK
        if (baselineStagnationLocation == null) {
            baselineStagnationLocation = currentLocation
            stagnationStartTime = System.currentTimeMillis()
        } else {
            val movementFromBaseline = currentLocation.distanceTo(baselineStagnationLocation!!)
            if (movementFromBaseline > 10.0f) {
                baselineStagnationLocation = currentLocation
                stagnationStartTime = System.currentTimeMillis()
            } else if (System.currentTimeMillis() - stagnationStartTime >= STAGNATION_TIME_THRESHOLD_MS) {
                currentRiskScore += WEIGHT_STAGNATION
                Log.w("RiskEngine", "[+30] Point Added: Stagnation > 10m")
            }
        }

        // 3. WRONG DIRECTION CHECK
        if (previousDistance != -1f && lastLocation != null) {
            val distChange = currentDistance - previousDistance
            // Check distance from last known point to avoid calculating direction on noise
            if (currentLocation.distanceTo(lastLocation!!) > JITTER_THRESHOLD) {
                val bearingToDest = lastLocation!!.bearingTo(destLocation)
                val actualMovementBearing = lastLocation!!.bearingTo(currentLocation)
                var angleDiff = Math.abs(bearingToDest - actualMovementBearing)
                if (angleDiff > 180) angleDiff = 360 - angleDiff

                if (distChange > 1.0f && angleDiff > 120) {
                    increasingDistanceCount++
                    val directionPenalty = increasingDistanceCount * WEIGHT_WRONG_DIRECTION
                    currentRiskScore += directionPenalty
                    Log.w("RiskEngine", "[+$directionPenalty] Points: Moving away. Trend: $increasingDistanceCount")
                } else if (distChange < 0) {
                    increasingDistanceCount = 0
                }
            }
        }

        // Final State Updates
        previousDistance = currentDistance
        lastLocation = currentLocation

        if (currentRiskScore > 0) {
            Log.d("RiskEngine", "Current Total Risk Score: $currentRiskScore / $RISK_THRESHOLD_TOTAL")
        }

        return if (currentRiskScore >= RISK_THRESHOLD_TOTAL) 1 else 0
    }

    fun reset() {
        previousDistance = -1f
        lastLocation = null
        increasingDistanceCount = 0
        stopStartTime = 0
        isCurrentlyStopped = false
        stopLocation = null
        stagnationStartTime = 0
        baselineStagnationLocation = null
        Log.d("RiskEngine", "Engine Reset")
    }
}