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
    data class Error(val message: String) : PhoneAuthState
}

/**
 * Handles Firebase Mobile Number OTP verification and sign-in.
 * Requires Phone Authentication + Play Integrity API enabled in Firebase Console.
 */
class PhoneAuthManager(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    private val _state = MutableStateFlow<PhoneAuthState>(PhoneAuthState.Idle)
    val state: StateFlow<PhoneAuthState> = _state.asStateFlow()

    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null
    private var activePhoneNumber: String = ""

    fun startPhoneNumberVerification(
        activity: Activity,
        rawPhoneNumber: String,
        isResend: Boolean = false
    ) {
        val formatted = rawPhoneNumber.trim().replace(" ", "")
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
                _state.value = PhoneAuthState.Error(
                    e.localizedMessage ?: "OTP verification failed. Check Firebase Console Phone Auth setup."
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

        val builder = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(formatted)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)

        if (isResend && resendToken != null) {
            builder.setForceResendingToken(resendToken!!)
        }

        PhoneAuthProvider.verifyPhoneNumber(builder.build())
    }

    suspend fun verifyOtpCode(verificationId: String, smsCode: String): Result<Unit> {
        val code = smsCode.trim()
        if (code.length < 6) {
            val msg = "Enter the 6-digit OTP code."
            _state.value = PhoneAuthState.Error(msg)
            return Result.failure(IllegalArgumentException(msg))
        }
        _state.value = PhoneAuthState.Verifying
        return try {
            val credential = PhoneAuthProvider.getCredential(verificationId, code)
            auth.signInWithCredential(credential).await()
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
        _state.value = PhoneAuthState.Verifying
        auth.signInWithCredential(credential)
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
