package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PolicyScreen(
    isTerms: Boolean,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val title = if (isTerms) "Terms & Conditions" else "Privacy Policy"
    val sections = if (isTerms) {
        listOf(
            "1. Acceptable Use" to "TIME is designed for respectful communication. Harassment, hate speech, impersonation, spam, and sharing non-consensual or unlawful media are strictly prohibited.",
            "2. Safety & Moderation" to "Users may block contacts and submit safety reports at any time. Reports are stored in Cloud Firestore and reviewed by moderation administrators.",
            "3. Permissions & Hardware" to "Camera and microphone permissions are used solely for live WebRTC video/audio calling and Video Call Studio. Media selection uses Android's zero-permission Photo Picker.",
            "4. Account & Data Deletion" to "You can sign out or delete your TIME cloud directory profile, private block/phone document, and authentication account from Profile -> Delete Account & Cloud Profile."
        )
    } else {
        listOf(
            "1. Cloud Firestore & Private Split" to "Public directory profiles (display name, handle, bio, online status) are stored in Cloud Firestore (/users/{uid}). Sensitive info such as phone numbers and blocked UIDs is isolated in a strictly owner-only private subcollection (/users/{uid}/private/info).",
            "2. Zero Broad Storage Permissions" to "TIME does not request READ_EXTERNAL_STORAGE, READ_MEDIA_IMAGES, or READ_MEDIA_VIDEO. Photos and video frames are picked safely using Android Photo Picker.",
            "3. Camera, Microphone & Notifications" to "CAMERA and RECORD_AUDIO permissions are requested only when using WebRTC calls or Video Call Studio. POST_NOTIFICATIONS is requested on Android 13+ only when enabled by the user.",
            "4. Authentication Security" to "TIME authenticates users via Firebase Auth (Google Sign-In via Jetpack Credential Manager and PhoneAuthManager OTP once Phone Auth + Play Integrity are enabled in Firebase Console)."
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("policy_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text(title) }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            sections.forEach { (heading, body) ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = heading,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirebaseGuideScreen(
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val steps = listOf(
        "1. सक्रिय सेवाएं (Already Provisioned)" to
            "Firebase Project (gen-lang-client-0418089832), Google Sign-In (Credential Manager), Cloud Firestore Database (ai-studio-android-time-96f54aac-a0c4-4798-a38b-c20dc1a3ccc5), और Zero-Trust Security Rules पहले ही सेट और कॉन्फ़िगर किए जा चुके हैं।",
        "2. Phone OTP Authentication चालू करें" to
            "PhoneAuthManager कोड ऐप में शामिल है। लाइव SMS OTP के लिए Firebase Console -> Authentication -> Sign-in method -> 'Phone' को Enable करें और Google Cloud Console में Play Integrity API चालू करें।",
        "3. Firebase Storage (बड़े वीडियो क्लिप के लिए)" to
            "अभी फोटो और वीडियो थंबनेल-फ्रेम Firestore के जरिए सिंक होते हैं। बड़े फुल-वीडियो अपलोड के लिए Firebase Console में Storage चालू करें।",
        "4. FCM Push Notifications (बैकग्राउंड अलर्ट)" to
            "ऐप खुले होने पर चैट और कॉल अलर्ट Firestore से तुरंत आते हैं। ऐप बंद होने पर बैकग्राउंड पुश नोटिफिकेशन के लिए Firebase Cloud Messaging + Cloud Functions सेट करें।",
        "5. Firebase App Check" to
            "प्रोडक्शन सुरक्षा के लिए Firebase Console -> App Check में अपने Android ऐप (com.aistudio.timechat.kpwmxr) के साथ Play Integrity रजिस्टर करें।",
        "6. WebRTC + TURN सर्वर सेटअप" to
            "अलग-अलग मोबाइल नेटवर्क पर रिलायबल वीडियो/ऑडियो कॉलिंग के लिए coturn या Metered TURN सर्वर तैयार करें और Video Call Studio -> Configure TURN में अपना turn: URL, username और credential सेव करें।",
        "7. Android APK बिल्ड निर्देश (Build Commands)" to
            "Debug APK के लिए: gradle :app:assembleDebug\nRelease APK के लिए: KEYSTORE_PATH, STORE_PASSWORD, KEY_PASSWORD सेट करके gradle :app:assembleRelease चलाएं (या GitHub Actions Release APK वर्कफ़्लो से डाउनलोड करें)।",
        "8. iPhone (iOS) वर्ज़न कैसे बिल्ड करें (Xcode / macOS)" to
            "यह Android बिल्ड सर्वर केवल Android APK बना सकता है, iOS (.ipa) नहीं। हालांकि रिपॉजिटरी के /ios-companion/ फ़ोल्डर में iPhone के लिए SwiftUI + Firebase + WebRTC (devicePlatform = 'IPHONE') सोर्स कोड दे दिया गया है। Mac पर Xcode में प्रोजेक्ट खोलें, GoogleService-Info.plist (प्रोजेक्ट: gen-lang-client-0418089832) जोड़ें और App Store / TestFlight पर चलाएं — Android और iPhone यूज़र एक साथ चैट और कॉल कर सकेंगे।",
        "9. GitHub Actions APK में google-services.json ऑटो-बंडल कैसे करें" to
            "Push to GitHub के समय सुरक्षा के लिए google-services.json रिपॉजिटरी में नहीं जाता। इसे ऑटोमैटिक जोड़ने के लिए: GitHub रिपॉजिटरी -> Settings -> Secrets and variables -> Actions -> New repository secret खोलें, Name में GOOGLE_SERVICES_JSON लिखें और AI Studio के app/google-services.json का पूरा JSON पेस्ट करें। इसके बाद हर GitHub APK बिल्ड अपने-आप तैयार खुलेगा।"
    )

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("guide_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Firebase सेटअप गाइड (हिन्दी)") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            steps.forEach { (heading, body) ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = heading,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
