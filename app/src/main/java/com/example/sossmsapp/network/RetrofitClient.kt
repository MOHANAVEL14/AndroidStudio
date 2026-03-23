package com.example.sossmsapp.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

// The Interface defining the Directions API call
interface GoogleDirectionsService {
    @GET("maps/api/directions/json")
    suspend fun getDirections(
        @Query("origin") origin: String,
        @Query("destination") destination: String,
        @Query("key") apiKey: String,
        @Query("mode") mode: String = "walking" // Default to walking for safety apps
    ): DirectionsResponse // We will define this model in Step 2
}

// Singleton to provide the Retrofit instance
object RetrofitClient {
    private const val BASE_URL = "https://maps.googleapis.com/"

    val instance: GoogleDirectionsService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GoogleDirectionsService::class.java)
    }
}