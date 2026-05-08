package com.example.ukfloodsdemographics

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

object FirebaseAuthRepository {


    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()


    suspend fun signInWithEmail(email: String, password: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                auth.signInWithEmailAndPassword(email.trim(), password).await()
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(Exception(authErrorMessage(e), e))
            }
        }


    suspend fun createAccount(
        displayName: String,
        email: String,
        password: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {


            val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
            val user = result.user ?: error("No User Returned")
            val profile = UserProfileChangeRequest.Builder()
                .setDisplayName(displayName.trim())
                .build()
            user.updateProfile(profile).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(authErrorMessage(e), e))
        }
    }


    fun signOut() {
        auth.signOut()
    }




    // Wrong email or password in the sign-in page
    fun isInvalidLoginCredentials(e: Throwable): Boolean {
        var t: Throwable? = e
        while (t != null) {
            if (t is FirebaseAuthException) {
                return when (t.errorCode) {
                    "ERROR WRONG PASSWORD",
                    "ERROR USER NOT FOUND",
                    "ERROR INVALID CREDENTIAL",
                    -> true
                    else -> false
                }
            }
            t = t.cause
        }
        return false
    }




    fun authErrorMessage(e: Throwable): String {
        var t: Throwable? = e
        while (t != null) {
            if (t is FirebaseAuthException) {
                val code = t.errorCode.orEmpty()
                val msg = t.message?.takeIf { it.isNotBlank() }
                return listOf(code, msg).filterNotNull().joinToString(" ").trim()
                    .ifEmpty { "Authentication failed ($code)" }
            }
            t = t.cause



        }
        t = e
        while (t != null) {
            val m = t.message.orEmpty()
            if (m.contains("CONFIGURATION_NOT_FOUND", ignoreCase = true)) return m
            t = t.cause
        }
        return e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
    }
}
