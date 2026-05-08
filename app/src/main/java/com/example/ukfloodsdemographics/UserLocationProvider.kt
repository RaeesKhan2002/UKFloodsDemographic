package com.example.ukfloodsdemographics

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

object UserLocationProvider {


    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }


     //I have attempted to return the best recent Location, or null if permission is missing

    suspend fun fetchBestLocation(context: Context): Location? {
        if (!hasLocationPermission(context)) return null
        val client = LocationServices.getFusedLocationProviderClient(context)
        return withContext(Dispatchers.IO) {
            try {
                val candidates = mutableListOf<Location>()
                client.lastLocation.await()?.let { candidates.add(it) }

                // Ask fused provider for a fresh GPS-capable fix first, then balanced fallback.
                listOf(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY
                ).forEach { priority ->
                    val token = CancellationTokenSource()
                    try {
                        client.getCurrentLocation(priority, token.token).await()?.let { candidates.add(it) }
                    } finally {
                        token.cancel()
                    }
                }

                candidates.minByOrNull { it.accuracy }
            } catch (_: Exception) {
                null
            }
        }
    }
}
