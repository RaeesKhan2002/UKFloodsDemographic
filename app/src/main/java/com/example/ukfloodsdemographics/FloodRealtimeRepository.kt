package com.example.ukfloodsdemographics

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

data class RemoteFloodAlert(
    val id: String,
    val postcode: String,
    val riskLevel: String,
    val latitude: Double,
    val longitude: Double,
    val createdByUid: String?,
    val createdAtMillis: Long
)

object FloodRealtimeRepository {
    private const val COLLECTION = "shared_flood_alerts"
    private const val EMAIL_COLLECTION = "mail"
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    suspend fun publishFlood(entry: FloodRiskEntry, createdByUid: String?) {
        val lat = entry.latitude ?: return
        val lon = entry.longitude ?: return
        val payload = hashMapOf(
            "postcode" to entry.postcode,
            "riskLevel" to entry.riskLevel,
            "latitude" to lat,
            "longitude" to lon,
            "createdByUid" to createdByUid,
            "createdAt" to Timestamp.now()
        )
        db.collection(COLLECTION).add(payload).await()
    }

    fun listenRecentFloods(
        onAlerts: (List<RemoteFloodAlert>) -> Unit,
        onError: (Throwable) -> Unit
    ): ListenerRegistration {
        return db.collection(COLLECTION)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(200)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }
                val docs = snapshot?.documents.orEmpty()
                val alerts = docs.mapNotNull { doc ->
                    val postcode = doc.getString("postcode") ?: return@mapNotNull null
                    val riskLevel = doc.getString("riskLevel") ?: "Unknown"
                    val lat = doc.getDouble("latitude") ?: return@mapNotNull null
                    val lon = doc.getDouble("longitude") ?: return@mapNotNull null
                    val createdByUid = doc.getString("createdByUid")
                    val createdAt = doc.getTimestamp("createdAt")?.toDate()?.time ?: 0L
                    RemoteFloodAlert(
                        id = doc.id,
                        postcode = postcode,
                        riskLevel = riskLevel,
                        latitude = lat,
                        longitude = lon,
                        createdByUid = createdByUid,
                        createdAtMillis = createdAt
                    )
                }
                onAlerts(alerts)
            }
    }

    suspend fun queueEmailNotification(to: String, subject: String, body: String) {
        val payload = hashMapOf(
            "to" to listOf(to),
            "message" to hashMapOf(
                "subject" to subject,
                "text" to body
            )
        )
        db.collection(EMAIL_COLLECTION).add(payload).await()
    }
}
