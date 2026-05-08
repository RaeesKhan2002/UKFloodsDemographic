package com.example.ukfloodsdemographics

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume


  //Help  Reverse-geocodes coordinates to a street name using Geocoder.
  //To stop duplicate work while scrolling results cached.

object StreetGeocoder {


    private val cache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val coordinatesCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Double, Double>>()


    private fun cacheKey(lat: Double, lon: Double): String =
        "${String.format(Locale.UK, "%.5f", lat)},${String.format(Locale.UK, "%.5f", lon)}"


    suspend fun streetFromCoordinates(context: Context, lat: Double?, lon: Double?): String? {
        if (lat == null || lon == null) return null
        val key = cacheKey(lat, lon)
        if (cache.containsKey(key)) return cache[key]!!.takeIf { it.isNotEmpty() }


        val resolved = withContext(Dispatchers.IO) {
            if (!Geocoder.isPresent()) return@withContext null
            val geocoder = Geocoder(context, Locale.UK)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocodeApi33Plus(geocoder, lat, lon)
            } else {

                @Suppress("DEPRECATION")

                try {

                    geocoder.getFromLocation(lat, lon, 1)?.firstOrNull()?.toStreetLine()
                } catch (_: Exception) {
                    null
                }
            }
        }

        cache[key] = resolved ?: ""
        return resolved
    }

    suspend fun coordinatesFromAddress(
        context: Context,
        postcode: String,
        addressLine: String
    ): Pair<Double, Double>? {
        val query = listOf(addressLine.trim(), postcode.trim(), "UK")
            .filter { it.isNotBlank() }
            .joinToString(", ")
        if (query.isBlank()) return null
        coordinatesCache[query]?.let { return it }


        val resolved = withContext(Dispatchers.IO) {
            if (!Geocoder.isPresent()) return@withContext null
            val geocoder = Geocoder(context, Locale.UK)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocodeLocationNameApi33Plus(geocoder, query)
            } else {

                @Suppress("DEPRECATION")
                try {
                    geocoder.getFromLocationName(query, 1)
                        ?.firstOrNull()
                        ?.let { it.latitude to it.longitude }
                } catch (_: Exception) {

                    null
                }
            }
        }

        if (resolved != null) {
            coordinatesCache[query] = resolved
        }

        return resolved
    }

    private suspend fun geocodeApi33Plus(
        geocoder: Geocoder,
        lat: Double,
        lon: Double
    ): String? = suspendCancellableCoroutine { cont ->
        geocoder.getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
            override fun onGeocode(addresses: MutableList<Address>) {
                if (cont.isCompleted) return
                cont.resume(addresses.firstOrNull()?.toStreetLine())
            }

            override fun onError(errorMessage: String?) {
                if (!cont.isCompleted) cont.resume(null)
            }
        })
    }

    private suspend fun geocodeLocationNameApi33Plus(
        geocoder: Geocoder,
        query: String
    ): Pair<Double, Double>? = suspendCancellableCoroutine { cont ->
        geocoder.getFromLocationName(query, 1, object : Geocoder.GeocodeListener {
            override fun onGeocode(addresses: MutableList<Address>) {
                if (cont.isCompleted) return
                val pair = addresses.firstOrNull()?.let { it.latitude to it.longitude }
                cont.resume(pair)
            }

            override fun onError(errorMessage: String?) {
                if (!cont.isCompleted) cont.resume(null)
            }
        })
    }

    private fun Address.toStreetLine(): String? {
        thoroughfare?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        getAddressLine(0)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        return null
    }
}
