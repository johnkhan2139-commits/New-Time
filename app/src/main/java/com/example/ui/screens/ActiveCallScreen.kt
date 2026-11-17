package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.data.preferences.TurnServerConfig
import com.example.data.remote.CallSession
import com.example.data.remote.IceCandidateDoc
import com.example.data.webrtc.WebRtcCallManager
import com.example.ui.components.AvatarCircle
import com.example.ui.theme.OnlineGreen
import kotlinx.coroutines.delay
import org.webrtc.PeerConnection
import org.webrtc.SurfaceViewRenderer

@Composable
fun ActiveCallScreen(
    callSession: CallSession,
    currentUserId: String,
    turnConfig: TurnServerConfig,
    remoteCandidates: List<IceCandidateDoc>,
    onPublishOffer: (String) -> Unit,
    onPublishAnswer: (String) -> Unit,
    onPublishIceCandidate: (sdpMid: String, sdpMLineIndex: Int, candidate: String) -> Unit,
    onHangUp: () -> Unit
) {
    BackHandler(onBack = onHangUp)

    val context = LocalContext.current
    val isVideoCall = callSession.callType == "VIDEO"
    val isCaller = callSession.callerId == currentUserId
    val peerName = if (isCaller) callSession.calleeName else callSession.callerName

    var hasPermissions by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
                (!isVideoCall || ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
        )
    }

    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val micOk = result[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val camOk = !isVideoCall || result[Manifest.permission.CAMERA] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        hasPermissions = micOk && camOk
    }

    var isMicMuted by remember { mutableStateOf(false) }
    var isCameraEnabled by remember { mutableStateOf(isVideoCall) }
    var isSpeakerOn by remember { mutableStateOf(isVideoCall) }
    var peerConnectionState by remember { mutableStateOf(PeerConnection.PeerConnectionState.NEW) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var offerSent by remember(callSession.id) { mutableStateOf(false) }
    var answerSent by remember(callSession.id) { mutableStateOf(false) }
    var appliedRemoteAnswer by remember(callSession.id) { mutableStateOf(false) }
    val appliedCandidateIds = remember(callSession.id) { mutableSetOf<String>() }

    val callManager = remember(callSession.id, hasPermissions) {
        if (!hasPermissions) null
        else {
            WebRtcCallManager(
                context = context,
                turnConfig = turnConfig,
                onLocalIceCandidate = { cand ->
                    onPublishIceCandidate(
                        cand.sdpMid ?: "",
                        cand.sdpMLineIndex,
                        cand.sdp
                    )
                },
                onConnectionStateChanged = { state ->
                    peerConnectionState = state
                },
                onRemoteVideoTrack = {}
            )
        }
    }

    DisposableEffect(callManager) {
        if (callManager != null) {
            callManager.initialize(isVideoCall)
            callManager.setSpeakerphoneEnabled(isSpeakerOn)
        }
        onDispose {
            callManager?.dispose()
        }
    }

    LaunchedEffect(callManager, isCaller) {
        if (callManager != null && isCaller && !offerSent && callSession.offerSdp.isNullOrBlank()) {
            offerSent = true
            callManager.createOffer(
                onOfferCreated = { sdp -> onPublishOffer(sdp) },
                onError = {}
            )
        }
    }

    LaunchedEffect(callManager, callSession.offerSdp, isCaller) {
        val offer = callSession.offerSdp
        if (callManager != null && !isCaller && !offer.isNullOrBlank() && !answerSent) {
            answerSent = true
            callManager.handleRemoteOfferAndCreateAnswer(
                offerSdp = offer,
                onAnswerCreated = { answer -> onPublishAnswer(answer) },
                onError = {}
            )
        }
    }

    LaunchedEffect(callManager, callSession.answerSdp, isCaller) {
        val answer = callSession.answerSdp
        if (callManager != null && isCaller && !answer.isNullOrBlank() && !appliedRemoteAnswer) {
            appliedRemoteAnswer = true
            callManager.applyRemoteAnswer(answer)
        }
    }

    LaunchedEffect(callManager, remoteCandidates) {
        if (callManager == null) return@LaunchedEffect
        remoteCandidates.forEach { doc ->
            if (doc.senderId != currentUserId && appliedCandidateIds.add(doc.id)) {
                callManager.addRemoteIceCandidate(
                    sdpMid = doc.sdpMid,
                    sdpMLineIndex = doc.sdpMLineIndex.toInt(),
                    candidateSdp = doc.candidate
                )
            }
        }
    }

    LaunchedEffect(callSession.status) {
        if (callSession.status == "ENDED" || callSession.status == "DECLINED") {
            delay(1000L)
            onHangUp()
        }
    }

    LaunchedEffect(callSession.status, peerConnectionState) {
        if (callSession.status == "ACTIVE") {
            while (true) {
                delay(1000L)
                elapsedSeconds += 1
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        if (hasPermissions && callManager != null && isVideoCall) {
            AndroidView(
                factory = { ctx ->
                    SurfaceViewRenderer(ctx).also { renderer ->
                        callManager.attachRemoteRenderer(renderer)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 72.dp, end = 16.dp)
                    .size(width = 112.dp, height = 164.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.5.dp, MaterialTheme.colorScheme.secondary, RoundedCornerShape(16.dp))
            ) {
                AndroidView(
                    factory = { ctx ->
                        SurfaceViewRenderer(ctx).also { renderer ->
                            callManager.attachLocalRenderer(renderer)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                AvatarCircle(name = peerName, size = 104.dp, isOnline = true)
                Text(
                    text = peerName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = when {
                        !hasPermissions -> "Grant Audio/Camera permissions to connect media"
                        callSession.status == "RINGING" -> "Ringing..."
                        callSession.status == "ACTIVE" -> "Audio Call • ${String.format("%02d:%02d", elapsedSeconds / 60, elapsedSeconds % 60)}"
                        else -> callSession.status
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.8f)
                )
                if (!hasPermissions) {
                    Button(
                        onClick = {
                            val req = if (isVideoCall) {
                                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                            } else {
                                arrayOf(Manifest.permission.RECORD_AUDIO)
                            }
                            permLauncher.launch(req)
                        }
                    ) {
                        Text("Allow Call Permissions")
                    }
                }
            }
        }

        // Top call bar
        Surface(
            color = Color.Black.copy(alpha = 0.65f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    text = peerName,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (peerConnectionState == PeerConnection.PeerConnectionState.CONNECTED) OnlineGreen
                                else MaterialTheme.colorScheme.secondary
                            )
                    )
                    Text(
                        text = when {
                            callSession.status == "RINGING" -> "Connecting via Firestore WebRTC..."
                            callSession.status == "ACTIVE" ->
                                "${peerConnectionState.name} • ${String.format("%02d:%02d", elapsedSeconds / 60, elapsedSeconds % 60)}"
                            else -> callSession.status
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }
        }

        // Bottom controls dock
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
                .clip(RoundedCornerShape(36.dp))
                .background(Color.Black.copy(alpha = 0.78f))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    isMicMuted = !isMicMuted
                    callManager?.setMicMuted(isMicMuted)
                },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (isMicMuted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant)
                    .testTag("call_mute_button")
            ) {
                Icon(
                    imageVector = if (isMicMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                    contentDescription = "Mute",
                    tint = Color.White
                )
            }

            IconButton(
                onClick = {
                    isSpeakerOn = !isSpeakerOn
                    callManager?.setSpeakerphoneEnabled(isSpeakerOn)
                },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (isSpeakerOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    .testTag("call_speaker_button")
            ) {
                Icon(
                    imageVector = if (isSpeakerOn) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                    contentDescription = "Speaker",
                    tint = Color.White
                )
            }

            if (isVideoCall) {
                IconButton(
                    onClick = {
                        isCameraEnabled = !isCameraEnabled
                        callManager?.setCameraEnabled(isCameraEnabled)
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (!isCameraEnabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant)
                        .testTag("call_camera_toggle_button")
                ) {
                    Icon(
                        imageVector = if (isCameraEnabled) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
                        contentDescription = "Toggle camera",
                        tint = Color.White
                    )
                }

                IconButton(
                    onClick = { callManager?.switchCamera() },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .testTag("call_switch_camera_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Cameraswitch,
                        contentDescription = "Switch camera",
                        tint = Color.White
                    )
                }
            }

            Button(
                onClick = onHangUp,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                shape = CircleShape,
                modifier = Modifier
                    .height(48.dp)
                    .testTag("call_end_button")
            ) {
                Icon(Icons.Filled.CallEnd, contentDescription = "End Call", tint = Color.White)
                Spacer(modifier = Modifier.width(6.dp))
                Text("End", color = Color.White)
            }
        }
    }
}
