package com.example.sossmsapp.utils

import android.location.Location
import com.google.android.gms.maps.model.LatLng

object RouteDeviationUtils {

    /**
     * Finds the shortest distance (in meters) from a [current] point
     * to any segment in the [polyline].
     */
    fun distanceFromRoute(current: LatLng, polyline: List<LatLng>): Float {
        if (polyline.isEmpty()) return -1f

        var minDistance = Float.MAX_VALUE

        for (i in 0 until polyline.size - 1) {
            val segmentStart = polyline[i]
            val segmentEnd = polyline[i + 1]

            val distance = distToSegment(current, segmentStart, segmentEnd)
            if (distance < minDistance) {
                minDistance = distance
            }
        }
        return minDistance
    }

    /**
     * Math: Shortest distance from point P to line segment AB
     */
    private fun distToSegment(p: LatLng, a: LatLng, b: LatLng): Float {
        val results = FloatArray(1)

        // Handle case where segment is just a point
        if (a.latitude == b.latitude && a.longitude == b.longitude) {
            Location.distanceBetween(p.latitude, p.longitude, a.latitude, a.longitude, results)
            return results[0]
        }

        // Calculate the projection of P onto the line segment AB
        val l2 = distSq(a, b)
        var t = ((p.latitude - a.latitude) * (b.latitude - a.latitude) +
                (p.longitude - a.longitude) * (b.longitude - a.longitude)) / l2

        t = Math.max(0.0, Math.min(1.0, t))

        val projection = LatLng(
            a.latitude + t * (b.latitude - a.latitude),
            a.longitude + t * (b.longitude - a.longitude)
        )

        Location.distanceBetween(p.latitude, p.longitude, projection.latitude, projection.longitude, results)
        return results[0]
    }

    private fun distSq(v: LatLng, w: LatLng): Double {
        return Math.pow(v.latitude - w.latitude, 2.0) + Math.pow(v.longitude - w.longitude, 2.0)
    }
}