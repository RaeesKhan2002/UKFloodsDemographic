package com.example.ukfloodsdemographics

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await
import java.util.Date



data class ChatMessage(
    val id: String = "",
    val text: String = "",
    val sender: String = "bot",
    val createdAt: Date = Date()
)



object ChatRepository {
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }



    fun observeMessages(
        userId: String,
        onUpdate: (List<ChatMessage>) -> Unit,
        onError: (Throwable) -> Unit
    ): ListenerRegistration {
        return db.collection("users")
            .document(userId)
            .collection("chatMessages")
            .orderBy("createdAt")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }

                

                val messages = snapshot?.documents.orEmpty().map { doc ->
                    val timestamp = doc.getTimestamp("createdAt") ?: Timestamp.now()
                    ChatMessage(
                        id = doc.id,
                        text = doc.getString("text").orEmpty(),
                        sender = doc.getString("sender").orEmpty().ifBlank { "bot" },
                        createdAt = timestamp.toDate()
                    )
                }
                onUpdate(messages)
            }
    }

    

    suspend fun sendMessage(userId: String, sender: String, text: String) {
        db.collection("users")
            .document(userId)
            .collection("chatMessages")
            .add(
                mapOf(
                    "sender" to sender,
                    "text" to text.trim(),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            .await()
    }
}
