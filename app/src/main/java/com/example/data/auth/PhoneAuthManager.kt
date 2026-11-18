package com.example.data.auth

import android.app.Activity
import android.util.Log
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

sealed interface PhoneAuthState {
    data object Idle : PhoneAuthState
    data object SendingCode : PhoneAuthState
    data class CodeSent(val verificationId: String, val phoneNumber: String) : PhoneAuthState
    data object Verifying : PhoneAuthState
    data object Verified : PhoneAuthState
    data class Error(
        val message: String,
        val needsConsoleEnable: Boolean = false
    ) : PhoneAuthState
}

/**
 * Handles Firebase Mobile Number OTP verification and sign-in.
 * Requires Phone Authentication + Play Integrity API enabled in Firebase Console.
 */
class PhoneAuthManager(
    private val providedAuth: FirebaseAuth? = null
) {
    private val auth: FirebaseAuth?
        get() = providedAuth ?: runCatching { FirebaseAuth.getInstance() }.getOrNull()

    private val _state = MutableStateFlow<PhoneAuthState>(PhoneAuthState.Idle)
    val state: StateFlow<PhoneAuthState> = _state.asStateFlow()

    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null
    private var activePhoneNumber: String = ""

    fun startPhoneNumberVerification(
        activity: Activity,
        rawPhoneNumber: String,
        isResend: Boolean = false
    ) {
        val firebaseAuth = auth
        if (firebaseAuth == null) {
            _state.value = PhoneAuthState.Error("Firebase is not configured in this APK build.")
            return
        }
        val formatted = rawPhoneNumber.trim().replace(Regex("[\\s\\-()]"), "")
        if (!formatted.startsWith("+") || formatted.length < 10) {
            _state.value = PhoneAuthState.Error("Enter phone number with country code (e.g. +919876543210).")
            return
        }

        activePhoneNumber = formatted
        _state.value = PhoneAuthState.SendingCode

        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                signInWithPhoneCredential(credential)
            }

            override fun onVerificationFailed(e: FirebaseException) {
                Log.e(TAG, "Phone verification failed", e)
                val rawMsg = e.localizedMessage.orEmpty()
                val providerDisabled = rawMsg.contains("not allowed", ignoreCase = true) ||
                    rawMsg.contains("disabled", ignoreCase = true)
                val authMismatch = rawMsg.contains("authorized", ignoreCase = true) ||
                    rawMsg.contains("integrity", ignoreCase = true) ||
                    rawMsg.contains("captcha", ignoreCase = true)

                val friendlyMsg = when {
                    providerDisabled ->
                        "Firebase Console me Phone Sign-In abhi DISABLED hai. Firebase Console -> Authentication -> Sign-in method me 'Phone' Enable karein, ya Google Sign-In use karein."
                    authMismatch ->
                        "Is APK ka SHA-1/SHA-256 ya Play Integrity Firebase Console me registered nahi hai. AI Studio APK use karein ya Firebase Console me SHA-1 + Play Integrity add karein."
                    else ->
                        rawMsg.ifBlank { "OTP verification failed. Check Firebase Console Phone Auth setup." }
                }
                _state.value = PhoneAuthState.Error(
                    message = friendlyMsg,
                    needsConsoleEnable = providerDisabled || authMismatch
                )
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                resendToken = token
                _state.value = PhoneAuthState.CodeSent(
                    verificationId = verificationId,
                    phoneNumber = formatted
                )
            }
        }

        val builder = PhoneAuthOptions.newBuilder(firebaseAuth)
            .setPhoneNumber(formatted)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)

        if (isResend && resendToken != null) {
            builder.setForceResendingToken(resendToken!!)
        }

        runCatching {
            PhoneAuthProvider.verifyPhoneNumber(builder.build())
        }.onFailure { err ->
            Log.e(TAG, "verifyPhoneNumber threw synchronously", err)
            _state.value = PhoneAuthState.Error(
                message = err.localizedMessage ?: "Failed to start OTP verification.",
                needsConsoleEnable = true
            )
        }
    }

    suspend fun verifyOtpCode(verificationId: String, smsCode: String): Result<Unit> {
        val firebaseAuth = auth
        if (firebaseAuth == null) {
            val msg = "Firebase is not configured in this APK build."
            _state.value = PhoneAuthState.Error(msg)
            return Result.failure(IllegalStateException(msg))
        }
        val code = smsCode.trim()
        if (code.length < 6) {
            val msg = "Enter the 6-digit OTP code."
            _state.value = PhoneAuthState.Error(msg)
            return Result.failure(IllegalArgumentException(msg))
        }
        _state.value = PhoneAuthState.Verifying
        return try {
            val credential = PhoneAuthProvider.getCredential(verificationId, code)
            firebaseAuth.signInWithCredential(credential).await()
            _state.value = PhoneAuthState.Verified
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "OTP sign-in failed", e)
            val msg = e.localizedMessage ?: "Invalid or expired OTP code."
            _state.value = PhoneAuthState.Error(msg)
            Result.failure(e)
        }
    }

    private fun signInWithPhoneCredential(credential: PhoneAuthCredential) {
        val firebaseAuth = auth ?: return
        _state.value = PhoneAuthState.Verifying
        firebaseAuth.signInWithCredential(credential)
            .addOnSuccessListener {
                _state.value = PhoneAuthState.Verified
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Auto phone credential sign-in failed", e)
                _state.value = PhoneAuthState.Error(
                    e.localizedMessage ?: "Failed to verify OTP credential."
                )
            }
    }

    fun reset() {
        _state.value = PhoneAuthState.Idle
    }

    private companion object {
        const val TAG = "PhoneAuthManager"
    }
}
