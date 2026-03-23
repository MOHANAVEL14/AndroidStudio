package com.example.sossmsapp.network

import com.google.gson.annotations.SerializedName

/**
 * Root response from Google Directions API
 */
data class DirectionsResponse(
    @SerializedName("routes") val routes: List<Route>,
    @SerializedName("status") val status: String
)

/**
 * A route contains several pieces of info, we just need the polyline
 */
data class Route(
    @SerializedName("overview_polyline") val overviewPolyline: OverviewPolyline
)

/**
 * The polyline is a single "encoded" string representing the whole path
 */
data class OverviewPolyline(
    @SerializedName("points") val points: String
)