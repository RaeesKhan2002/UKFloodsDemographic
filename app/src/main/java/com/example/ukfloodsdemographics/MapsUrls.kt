package com.example.ukfloodsdemographics



import android.net.Uri



fun googleMapsSearchUrl(latitude: Double?, longitude: Double?, postcode: String): String {
    val query = if (latitude != null && longitude != null) {
        "$latitude,$longitude"
    } else {
        postcode
    }

    return "https://www.google.com/maps/search/?api=1&query=${Uri.encode(query)}"
}
