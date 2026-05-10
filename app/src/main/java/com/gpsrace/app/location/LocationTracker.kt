package com.gpsrace.app.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class LocationTracker(private val context: Context) {
    private val fusedClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
    @SuppressLint("MissingPermission")
    fun locationFlow(intervalMs: Long = 1_000L, fastestIntervalMs: Long = 500L, minDisplacementM: Float = 0.5f): Flow<Location> = callbackFlow {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs).setMinUpdateIntervalMillis(fastestIntervalMs).setMinUpdateDistanceMeters(minDisplacementM).build()
        val callback = object : LocationCallback() { override fun onLocationResult(r: LocationResult) { r.lastLocation?.let { trySend(it) } } }
        fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
        awaitClose { fusedClient.removeLocationUpdates(callback) }
    }
}
