package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.auth.GoogleAuthManager
import com.example.data.auth.PhoneAuthManager
import com.example.data.auth.PhoneAuthState
import com.example.ui.components.TimeBrandHeader
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    credentialManager: CredentialManager,
    onAuthenticated: () -> Unit,
    onOpenFirebaseGuide: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val phoneAuthManager = remember { PhoneAuthManager() }
    val phoneState by phoneAuthManager.state.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) } // 0 = Google, 1 = Mobile OTP
    var googleLoading by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }

    var phoneInput by rememberSaveable { mutableStateOf("+91") }
    var otpCode by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(phoneState) {
        if (phoneState is PhoneAuthState.Verified) {
            onAuthenticated()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TimeBrandHeader()

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(176.dp)
                    .clip(RoundedCornerShape(22.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_time_hero),
                    contentDescription = "TIME wave artwork",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.94f)
                                )
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Text(
                        text = "TIME Online",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Public directory, 1-to-1 & group messaging, photos/videos & WebRTC calls.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilterChip(
                    selected = selectedTab == 0,
                    onClick = {
                        selectedTab = 0
                        authError = null
                    },
                    label = { Text("Google Sign-In") },
                    leadingIcon = { Icon(Icons.Filled.AccountCircle, contentDescription = null) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("auth_tab_google")
                )
                FilterChip(
                    selected = selectedTab == 1,
                    onClick = {
                        selectedTab = 1
                        authError = null
                    },
                    label = { Text("Mobile OTP") },
                    leadingIcon = { Icon(Icons.Filled.Phone, contentDescription = null) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("auth_tab_phone_otp")
                )
            }

            if (authError != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = authError ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            if (selectedTab == 0) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Sign in with Google",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Instant verified login provisioned with your TIME Cloud Firestore directory, groups, and WebRTC signaling.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Button(
                            onClick = {
                                if (googleLoading) return@Button
                                googleLoading = true
                                authError = null
                                GoogleAuthManager.onGoogleSignInClicked(
                                    context = context,
                                    credentialManager = credentialManager,
                                    onAuthSuccess = {
                                        googleLoading = false
                                        onAuthenticated()
                                    },
                                    onAuthError = { msg ->
                                        googleLoading = false
                                        authError = msg
                                    },
                                    onAuthCancelled = {
                                        googleLoading = false
                                    },
                                    scope = scope
                                )
                            },
                            enabled = !googleLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("google_sign_in_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            if (googleLoading) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Continue with Google")
                            }
                        }
                    }
                }
            } else {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Mobile Number OTP Login",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Enter your mobile number with country code (e.g. +919876543210). Requires Phone Auth + Play Integrity enabled in Firebase Console (gen-lang-client-0418089832).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = phoneInput,
                            onValueChange = { phoneInput = it },
                            label = { Text("Mobile Number with Country Code") },
                            placeholder = { Text("+919876543210") },
                            leadingIcon = { Icon(Icons.Filled.Phone, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("otp_phone_input")
                        )

                        when (val pState = phoneState) {
                            is PhoneAuthState.CodeSent -> {
                                OutlinedTextField(
                                    value = otpCode,
                                    onValueChange = { otpCode = it },
                                    label = { Text("6-Digit OTP Code") },
                                    leadingIcon = { Icon(Icons.Filled.Sms, contentDescription = null) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("otp_code_input")
                                )

                                Button(
                                    onClick = {
                                        scope.launch {
                                            phoneAuthManager.verifyOtpCode(pState.verificationId, otpCode)
                                        }
                                    },
                                    enabled = otpCode.trim().length >= 6,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("verify_otp_button")
                                ) {
                                    Text("Verify OTP & Sign In")
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    TextButton(onClick = { phoneAuthManager.reset() }) {
                                        Text("Change Number")
                                    }
                                    TextButton(
                                        onClick = {
                                            if (activity != null) {
                                                phoneAuthManager.startPhoneNumberVerification(
                                                    activity = activity,
                                                    rawPhoneNumber = phoneInput,
                                                    isResend = true
                                                )
                                            }
                                        }
                                    ) {
                                        Text("Resend OTP")
                                    }
                                }
                            }

                            is PhoneAuthState.SendingCode, is PhoneAuthState.Verifying -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        if (pState is PhoneAuthState.SendingCode) "Requesting SMS OTP..." else "Verifying OTP..."
                                    )
                                }
                            }

                            is PhoneAuthState.Error -> {
                                Text(
                                    text = pState.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                                if (pState.needsConsoleEnable) {
                                    OutlinedButton(
                                        onClick = {
                                            val projectId = runCatching {
                                                FirebaseApp.getInstance().options.projectId
                                            }.getOrNull()?.takeIf { !it.isNullOrBlank() }
                                                ?: "gen-lang-client-0418089832"
                                            runCatching {
                                                context.startActivity(
                                                    Intent(
                                                        Intent.ACTION_VIEW,
                                                        Uri.parse("https://console.firebase.google.com/project/$projectId/authentication/providers")
                                                    )
                                                )
                                            }.onFailure { err ->
                                                authError = err.localizedMessage ?: "Unable to open browser."
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("open_phone_auth_console_button")
                                    ) {
                                        Text("Enable Phone Auth in Firebase Console")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            selectedTab = 0
                                            authError = null
                                            phoneAuthManager.reset()
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("switch_to_google_signin_button")
                                    ) {
                                        Text("Use Google Sign-In (Active Now)")
                                    }
                                }
                                Button(
                                    onClick = {
                                        if (activity != null) {
                                            phoneAuthManager.startPhoneNumberVerification(activity, phoneInput)
                                        } else {
                                            authError = "Activity context unavailable."
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("send_otp_button")
                                ) {
                                    Text("Send OTP Again")
                                }
                            }

                            else -> {
                                Button(
                                    onClick = {
                                        if (activity != null) {
                                            phoneAuthManager.startPhoneNumberVerification(activity, phoneInput)
                                        } else {
                                            authError = "Activity context unavailable."
                                        }
                                    },
                                    enabled = phoneInput.trim().length >= 10,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("send_otp_button")
                                ) {
                                    Text("Send Mobile OTP")
                                }
                            }
                        }
                    }
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenFirebaseGuide)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Text(
                        text = "Cloud Firestore & Google Sign-In are active. Tap here for Hindi instructions on enabling SMS Phone Auth, Firebase Storage, FCM Push & TURN servers.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            OutlinedButton(
                onClick = onOpenFirebaseGuide,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("auth_firebase_guide_button")
            ) {
                Text("Firebase Setup Guide (हिन्दी + English)")
            }
        }
    }
}
