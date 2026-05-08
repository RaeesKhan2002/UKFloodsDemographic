package com.example.ukfloodsdemographics

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.clustering.ClusterItem

//Map markers for the flood-risk postcode point from the CSV File .

data class FloodMapClusterItem(
    val entry: FloodRiskEntry
) : ClusterItem {
    override fun getPosition(): LatLng =
        LatLng(entry.latitude!!, entry.longitude!!)


    override fun getTitle(): String = entry.postcode


    override fun getSnippet(): String = buildString {
        append(entry.riskLevel)
        entry.detail?.let { append("\n").append(it) }
    }


    override fun getZIndex(): Float? = null
}
