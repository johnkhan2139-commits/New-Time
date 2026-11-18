package com.example.data.webrtc

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.example.data.preferences.TurnServerConfig
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

class WebRtcCallManager(
    private val context: Context,
    private val turnConfig: TurnServerConfig,
    private val onLocalIceCandidate: (IceCandidate) -> Unit,
    private val onConnectionStateChanged: (PeerConnection.PeerConnectionState) -> Unit,
    private val onRemoteVideoTrack: (VideoTrack) -> Unit
) {
    val eglBase: EglBase = EglBase.create()

    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var videoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null

    private var localRenderer: SurfaceViewRenderer? = null
    private var remoteRenderer: SurfaceViewRenderer? = null
    private var activeRemoteTrack: VideoTrack? = null

    private val pendingIceCandidates = mutableListOf<IceCandidate>()
    private var remoteDescriptionSet = false
    private var isDisposed = false

    fun initialize(isVideoCall: Boolean) {
        if (factory == null) {
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                    .setEnableInternalTracer(false)
                    .createInitializationOptions()
            )
            val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
            val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)
            factory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .createPeerConnectionFactory()
        }

        val iceServers = buildIceServers()
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        peerConnection = factory?.createPeerConnection(
            rtcConfig,
            object : PeerConnection.Observer {
                override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {}
                override fun onIceConnectionReceivingChange(receiving: Boolean) {}
                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
                override fun onIceCandidate(candidate: IceCandidate?) {
                    candidate?.let { onLocalIceCandidate(it) }
                }

                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
                override fun onAddStream(stream: MediaStream?) {
                    val track = stream?.videoTracks?.firstOrNull()
                    if (track != null) {
                        attachRemoteTrack(track)
                    }
                }

                override fun onRemoveStream(stream: MediaStream?) {}
                override fun onDataChannel(channel: DataChannel?) {}
                override fun onRenegotiationNeeded() {}
                override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                    val track = receiver?.track()
                    if (track is VideoTrack) {
                        attachRemoteTrack(track)
                    }
                }

                override fun onTrack(transceiver: RtpTransceiver?) {
                    val track = transceiver?.receiver?.track()
                    if (track is VideoTrack) {
                        attachRemoteTrack(track)
                    }
                }

                override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                    newState?.let { onConnectionStateChanged(it) }
                }
            }
        )

        setupLocalMedia(isVideoCall)
    }

    private fun buildIceServers(): List<PeerConnection.IceServer> {
        val servers = mutableListOf<PeerConnection.IceServer>()
        val stunUrl = turnConfig.stunUrl.ifBlank { "stun:stun.l.google.com:19302" }
        servers.add(PeerConnection.IceServer.builder(stunUrl).createIceServer())
        if (turnConfig.isTurnConfigured) {
            servers.add(
                PeerConnection.IceServer.builder(turnConfig.turnUrl)
                    .setUsername(turnConfig.username)
                    .setPassword(turnConfig.credential)
                    .createIceServer()
            )
        }
        return servers
    }

    private fun setupLocalMedia(isVideoCall: Boolean) {
        val pcf = factory ?: return
        val audioConstraints = MediaConstraints()
        audioSource = pcf.createAudioSource(audioConstraints)
        localAudioTrack = pcf.createAudioTrack("TIME_AUDIO_0", audioSource)?.apply {
            setEnabled(true)
            peerConnection?.addTrack(this, listOf("TIME_STREAM"))
        }

        if (isVideoCall) {
            val capturer = createCameraCapturer()
            if (capturer != null) {
                videoCapturer = capturer
                surfaceTextureHelper = SurfaceTextureHelper.create("TimeCaptureThread", eglBase.eglBaseContext)
                videoSource = pcf.createVideoSource(capturer.isScreencast)
                capturer.initialize(surfaceTextureHelper, context.applicationContext, videoSource?.capturerObserver)
                runCatching { capturer.startCapture(720, 1280, 30) }
                    .onFailure { Log.w(TAG, "Failed to start camera capture: ${it.message}") }

                localVideoTrack = pcf.createVideoTrack("TIME_VIDEO_0", videoSource)?.apply {
                    setEnabled(true)
                    localRenderer?.let { addSink(it) }
                    peerConnection?.addTrack(this, listOf("TIME_STREAM"))
                }
            }
        }
    }

    private fun createCameraCapturer(): CameraVideoCapturer? {
        val enumerator = Camera2Enumerator(context)
        val deviceNames = enumerator.deviceNames
        val front = deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
        val back = deviceNames.firstOrNull { enumerator.isBackFacing(it) }
        val chosen = front ?: back ?: return null
        return enumerator.createCapturer(chosen, null)
    }

    fun attachLocalRenderer(renderer: SurfaceViewRenderer) {
        if (localRenderer === renderer) return
        localRenderer = renderer
        renderer.init(eglBase.eglBaseContext, null)
        renderer.setMirror(true)
        renderer.setEnableHardwareScaler(true)
        renderer.setZOrderMediaOverlay(true)
        localVideoTrack?.addSink(renderer)
    }

    fun attachRemoteRenderer(renderer: SurfaceViewRenderer) {
        if (remoteRenderer === renderer) return
        remoteRenderer = renderer
        renderer.init(eglBase.eglBaseContext, null)
        renderer.setMirror(false)
        renderer.setEnableHardwareScaler(true)
        activeRemoteTrack?.addSink(renderer)
    }

    private fun attachRemoteTrack(track: VideoTrack) {
        activeRemoteTrack = track
        remoteRenderer?.let { track.addSink(it) }
        onRemoteVideoTrack(track)
    }

    fun createOffer(onOfferCreated: (String) -> Unit, onError: (String) -> Unit) {
        val pc = peerConnection ?: return onError("PeerConnection not initialized")
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }
        pc.createOffer(object : SdpObserverAdapter() {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                if (sdp == null) {
                    onError("Empty SDP offer")
                    return
                }
                pc.setLocalDescription(object : SdpObserverAdapter() {
                    override fun onSetSuccess() {
                        onOfferCreated(sdp.description)
                    }

                    override fun onSetFailure(err: String?) {
                        onError(err ?: "Failed to set local offer")
                    }
                }, sdp)
            }

            override fun onCreateFailure(err: String?) {
                onError(err ?: "Failed to create offer")
            }
        }, constraints)
    }

    fun handleRemoteOfferAndCreateAnswer(
        offerSdp: String,
        onAnswerCreated: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val pc = peerConnection ?: return onError("PeerConnection not initialized")
        val offer = SessionDescription(SessionDescription.Type.OFFER, offerSdp)
        pc.setRemoteDescription(object : SdpObserverAdapter() {
            override fun onSetSuccess() {
                remoteDescriptionSet = true
                flushPendingIceCandidates()
                val constraints = MediaConstraints().apply {
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
                }
                pc.createAnswer(object : SdpObserverAdapter() {
                    override fun onCreateSuccess(sdp: SessionDescription?) {
                        val answer = sdp
                        if (answer == null) {
                            onError("Empty SDP answer")
                            return
                        }
                        pc.setLocalDescription(object : SdpObserverAdapter() {
                            override fun onSetSuccess() {
                                onAnswerCreated(answer.description)
                            }

                            override fun onSetFailure(err: String?) {
                                onError(err ?: "Failed to set local answer")
                            }
                        }, answer)
                    }

                    override fun onCreateFailure(err: String?) {
                        onError(err ?: "Failed to create answer")
                    }
                }, constraints)
            }

            override fun onSetFailure(err: String?) {
                onError(err ?: "Failed to apply remote offer")
            }
        }, offer)
    }

    fun applyRemoteAnswer(answerSdp: String, onError: (String) -> Unit = {}) {
        val pc = peerConnection ?: return
        if (remoteDescriptionSet) return
        val answer = SessionDescription(SessionDescription.Type.ANSWER, answerSdp)
        pc.setRemoteDescription(object : SdpObserverAdapter() {
            override fun onSetSuccess() {
                remoteDescriptionSet = true
                flushPendingIceCandidates()
            }

            override fun onSetFailure(err: String?) {
                onError(err ?: "Failed to set remote answer")
            }
        }, answer)
    }

    fun addRemoteIceCandidate(sdpMid: String, sdpMLineIndex: Int, candidateSdp: String) {
        val candidate = IceCandidate(sdpMid, sdpMLineIndex, candidateSdp)
        if (remoteDescriptionSet) {
            peerConnection?.addIceCandidate(candidate)
        } else {
            pendingIceCandidates.add(candidate)
        }
    }

    private fun flushPendingIceCandidates() {
        val pc = peerConnection ?: return
        pendingIceCandidates.forEach { pc.addIceCandidate(it) }
        pendingIceCandidates.clear()
    }

    fun setMicMuted(muted: Boolean) {
        localAudioTrack?.setEnabled(!muted)
    }

    fun setCameraEnabled(enabled: Boolean) {
        localVideoTrack?.setEnabled(enabled)
    }

    fun switchCamera() {
        videoCapturer?.switchCamera(null)
    }

    fun setSpeakerphoneEnabled(enabled: Boolean) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        @Suppress("DEPRECATION")
        audioManager.isSpeakerphoneOn = enabled
    }

    fun dispose() {
        if (isDisposed) return
        isDisposed = true
        runCatching { videoCapturer?.stopCapture() }
        runCatching { videoCapturer?.dispose() }
        runCatching { surfaceTextureHelper?.dispose() }
        runCatching { localRenderer?.let { localVideoTrack?.removeSink(it) } }
        runCatching { remoteRenderer?.let { activeRemoteTrack?.removeSink(it) } }
        runCatching { localRenderer?.release() }
        runCatching { remoteRenderer?.release() }
        runCatching { videoSource?.dispose() }
        runCatching { audioSource?.dispose() }
        runCatching { peerConnection?.close() }
        runCatching { peerConnection?.dispose() }
        runCatching { factory?.dispose() }
        runCatching { eglBase.release() }
    }

    private open class SdpObserverAdapter : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(err: String?) {}
        override fun onSetFailure(err: String?) {}
    }

    private companion object {
        const val TAG = "WebRtcCallManager"
    }
}
