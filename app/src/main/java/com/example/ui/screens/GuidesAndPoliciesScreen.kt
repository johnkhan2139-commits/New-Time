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
            "2. Safety & Moderation" to "Users may block contacts and submit safety reports at any time. Once connected to a Firebase backend, moderation actions apply across your public directory and chat rooms.",
            "3. Permissions & Hardware" to "Camera and microphone permissions are used solely for live video call preview and WebRTC sessions. Media selection uses Android's zero-permission Photo Picker.",
            "4. Account & Data Deletion" to "You can sign out or delete all locally stored profile, message, and media records at any time from Profile -> Delete Account & Local Data."
        )
    } else {
        listOf(
            "1. Local-First Data Storage" to "Because Firebase provisioning was declined during setup, all profile details, chat threads, attachments, and safety reports are stored strictly on your device inside TIME's private Room database and files directory.",
            "2. Zero Broad Storage Permissions" to "TIME does not request READ_EXTERNAL_STORAGE, READ_MEDIA_IMAGES, or READ_MEDIA_VIDEO. You explicitly choose photos or videos via the system Android Photo Picker.",
            "3. Camera, Microphone & Notifications" to "CAMERA and RECORD_AUDIO permissions are requested only when using Video Call Studio. POST_NOTIFICATIONS is requested on Android 13+ only when enabled by the user.",
            "4. Cloud & Firebase Integration" to "When you later attach your own Firebase project (google-services.json), Phone OTP Auth, Firestore, Cloud Storage, FCM, and App Check will operate under your Firebase project's security rules."
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
        "1. Firebase प्रोजेक्ट बनाएं (Create Project)" to
            "Firebase Console (console.firebase.google.com) पर जाएं -> 'Add Project' पर क्लिक करें -> नाम 'TIME' रखें -> Android ऐप रजिस्टर करें (Package ID: com.aistudio.timechat.kpwmxr) और अपना SHA-1 / SHA-256 फिंगरप्रिंट जोड़ें।",
        "2. google-services.json डाउनलोड करें" to
            "डाउनलोड की गई google-services.json फाइल को प्रोजेक्ट के app/ फोल्डर (app/google-services.json) में रखें। कभी भी नकली या placeholder क्रेडेंशियल इस्तेमाल न करें।",
        "3. Phone Authentication (मोबाइल OTP लॉगिन)" to
            "Firebase Console -> Authentication -> Sign-in method -> 'Phone' को Enable करें। प्रोडक्शन SMS OTP के लिए Play Integrity API / App Check को सक्रिय करें।",
        "4. Cloud Firestore & Security Rules" to
            "Firestore Database बनाएं और नियम (Rules) सेट करें ताकि केवल लॉग-इन यूज़र (request.auth != null) ही अपने चैट रूम, प्रोफाइल और सुरक्षा रिपोर्ट पढ़/लिख सकें।",
        "5. Firebase Storage & Rules" to
            "फोटो और वीडियो शेयरिंग के लिए Firebase Storage चालू करें और साइज व Auth आधारित Storage Rules लागू करें।",
        "6. Firebase Cloud Messaging (FCM) & App Check" to
            "चैट और इनकमिंग कॉल नोटिफिकेशन के लिए Cloud Messaging चालू करें। Play Integrity के साथ Firebase App Check रजिस्टर करें।",
        "7. WebRTC + TURN सर्वर सेटअप" to
            "रिलायबल वीडियो कॉलिंग के लिए coturn या Metered/Twilio TURN सर्वर तैयार करें और Video Call Studio -> Configure TURN में अपना turn: URL, username और credential सेव करें।",
        "8. Android APK बिल्ड निर्देश (Build Commands)" to
            "Debug APK के लिए: gradle :app:assembleDebug\nRelease APK के लिए: कीस्टोर env वेरिएबल्स (KEYSTORE_PATH, STORE_PASSWORD, KEY_PASSWORD) सेट करके gradle :app:assembleRelease चलाएं।"
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
