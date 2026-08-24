package com.domofon.app.ui

import android.content.Context
import android.util.Log
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SessionDescription
import org.webrtc.SurfaceViewRenderer
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "DomofonWebRTC"

private fun log(msg: String) = Log.d(TAG, msg)

private fun parseIceServers(json: String): List<PeerConnection.IceServer> {
    val out = mutableListOf<PeerConnection.IceServer>()
    val arr = runCatching { JSONArray(json) }.getOrNull() ?: JSONArray()
    for (i in 0 until arr.length()) {
        val item = arr.optJSONObject(i) ?: continue
        val urls = mutableListOf<String>()
        when (val raw = item.opt("urls")) {
            is String -> if (raw.isNotBlank()) urls += raw
            is JSONArray -> for (j in 0 until raw.length()) {
                raw.optString(j).takeIf { it.isNotBlank() }?.let { urls += it }
            }
        }
        if (urls.isEmpty()) continue
        val builder = PeerConnection.IceServer.builder(urls)
        val user = item.optString("username")
        val cred = item.optString("credential").ifBlank { item.optString("password") }
        if (user.isNotBlank() && cred.isNotBlank()) {
            builder.setUsername(user).setPassword(cred)
        }
        out += builder.createIceServer()
    }
    if (out.none { it.urls.any { u -> u.startsWith("stun:") } }) {
        out += PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
    }
    return out
}

private fun needsRelayOnly(iceServers: List<PeerConnection.IceServer>): Boolean =
    iceServers.any { server ->
        server.urls.any { url ->
            url.startsWith("turns:") && (url.contains(":443") || url.contains("443?"))
        }
    }

private class WhepSession(
    context: Context,
    private val renderer: SurfaceViewRenderer,
    private val iceServersJson: String,
) {
    private val executor = Executors.newSingleThreadExecutor()
    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private val egl = EglBase.create()
    private val factory: PeerConnectionFactory
    private var peer: PeerConnection? = null
    private var currentUrl: String? = null
    private val resourceUrl = AtomicReference<String?>(null)

    init {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                .setEnableInternalTracer(false)
                .createInitializationOptions(),
        )
        val encoder = DefaultVideoEncoderFactory(egl.eglBaseContext, true, true)
        val decoder = DefaultVideoDecoderFactory(egl.eglBaseContext)
        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoder)
            .setVideoDecoderFactory(decoder)
            .createPeerConnectionFactory()
        renderer.init(egl.eglBaseContext, null)
        renderer.setMirror(false)
        renderer.setEnableHardwareScaler(true)
    }

    fun play(whepUrl: String) {
        if (whepUrl.isBlank()) return
        if (whepUrl == currentUrl) return
        log("play url=$whepUrl")
        currentUrl = whepUrl
        executor.execute {
            runCatching { connect(whepUrl) }
                .onFailure {
                    log("connect failed: ${it.message}")
                    it.printStackTrace()
                }
        }
    }

    private fun connect(whepUrl: String) {
        closePeerLocked()
        val iceServers = parseIceServers(iceServersJson)
        val relayOnly = needsRelayOnly(iceServers)
        log(
            "ICE servers=${iceServers.size} relayOnly=$relayOnly urls=" +
                iceServers.flatMap { it.urls }.joinToString(),
        )
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            if (relayOnly) {
                iceTransportsType = PeerConnection.IceTransportsType.RELAY
                log("iceTransportsType=RELAY (turns:443 / LTE)")
            } else {
                log("iceTransportsType=ALL (direct host/prflx)")
            }
        }
        val observer = object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {
                log("signalingState=$state")
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                log("iceConnectionState=$state")
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) {
                log("iceReceiving=$receiving")
            }

            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                log("iceGatheringState=$state")
            }

            override fun onIceCandidate(candidate: org.webrtc.IceCandidate?) {
                candidate ?: return
                log("localCandidate ${candidate.sdpMid}: ${candidate.sdp.take(120)}")
            }

            override fun onIceCandidatesRemoved(candidates: Array<out org.webrtc.IceCandidate>?) = Unit
            override fun onAddStream(stream: org.webrtc.MediaStream?) = Unit
            override fun onRemoveStream(stream: org.webrtc.MediaStream?) = Unit
            override fun onDataChannel(dc: org.webrtc.DataChannel?) = Unit
            override fun onRenegotiationNeeded() = Unit
            override fun onAddTrack(
                receiver: org.webrtc.RtpReceiver?,
                streams: Array<out org.webrtc.MediaStream>?,
            ) {
                val track = receiver?.track() as? org.webrtc.VideoTrack ?: return
                log("videoTrack id=${track.id()} enabled=${track.enabled()}")
                track.addSink(renderer)
            }
        }
        val pc = factory.createPeerConnection(rtcConfig, observer)
            ?: error("PeerConnection failed")
        peer = pc
        pc.addTransceiver(
            org.webrtc.MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
            org.webrtc.RtpTransceiver.RtpTransceiverInit(
                org.webrtc.RtpTransceiver.RtpTransceiverDirection.RECV_ONLY,
            ),
        )
        pc.addTransceiver(
            org.webrtc.MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO,
            org.webrtc.RtpTransceiver.RtpTransceiverInit(
                org.webrtc.RtpTransceiver.RtpTransceiverDirection.RECV_ONLY,
            ),
        )

        val constraints = MediaConstraints()
        val offerLatch = java.util.concurrent.CountDownLatch(1)
        val offerBox = arrayOfNulls<SessionDescription>(1)
        val offerError = arrayOfNulls<String>(1)
        log("createOffer…")
        pc.createOffer(object : org.webrtc.SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                offerBox[0] = desc
                offerLatch.countDown()
            }
            override fun onSetSuccess() = Unit
            override fun onCreateFailure(error: String?) {
                offerError[0] = error
                offerLatch.countDown()
            }
            override fun onSetFailure(error: String?) {
                offerError[0] = error
                offerLatch.countDown()
            }
        }, constraints)
        offerLatch.await(10, TimeUnit.SECONDS)
        offerError[0]?.let { error(it) }
        val offer = offerBox[0] ?: error("Empty offer")

        val setLocalLatch = java.util.concurrent.CountDownLatch(1)
        val setLocalError = arrayOfNulls<String>(1)
        pc.setLocalDescription(object : org.webrtc.SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) = Unit
            override fun onSetSuccess() = setLocalLatch.countDown()
            override fun onCreateFailure(error: String?) {
                setLocalError[0] = error
                setLocalLatch.countDown()
            }
            override fun onSetFailure(error: String?) {
                setLocalError[0] = error
                setLocalLatch.countDown()
            }
        }, offer)
        setLocalLatch.await(10, TimeUnit.SECONDS)
        setLocalError[0]?.let { error(it) }

        log("WHEP POST $whepUrl")
        val request = Request.Builder()
            .url(whepUrl)
            .header("Content-Type", "application/sdp")
            .post(offer.description.toRequestBody("application/sdp".toMediaType()))
            .build()
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            log("WHEP response HTTP ${response.code} bodyLen=${body.length}")
            if (!response.isSuccessful) error("WHEP HTTP ${response.code}: $body")
            response.header("Location")?.let {
                resourceUrl.set(it)
                log("WHEP resource=$it")
            }
            val answer = SessionDescription(SessionDescription.Type.ANSWER, body)
            val setRemoteLatch = java.util.concurrent.CountDownLatch(1)
            val setRemoteError = arrayOfNulls<String>(1)
            pc.setRemoteDescription(object : org.webrtc.SdpObserver {
                override fun onCreateSuccess(p0: SessionDescription?) = Unit
                override fun onSetSuccess() = setRemoteLatch.countDown()
                override fun onCreateFailure(error: String?) {
                    setRemoteError[0] = error
                    setRemoteLatch.countDown()
                }
                override fun onSetFailure(error: String?) {
                    setRemoteError[0] = error
                    setRemoteLatch.countDown()
                }
            }, answer)
            setRemoteLatch.await(10, TimeUnit.SECONDS)
            setRemoteError[0]?.let { error(it) }
            log("remoteDescription set, waiting ICE…")
        }
    }

    private fun closePeerLocked() {
        resourceUrl.getAndSet(null)?.let { url ->
            log("WHEP DELETE $url")
            runCatching {
                http.newCall(Request.Builder().url(url).delete().build()).execute().close()
            }
        }
        peer?.close()
        peer?.dispose()
        peer = null
    }

    fun release() {
        log("release")
        executor.execute {
            closePeerLocked()
            runCatching { renderer.release() }
            runCatching { factory.dispose() }
            runCatching { egl.release() }
        }
        executor.shutdown()
    }
}

@Composable
fun WebRtcPlayer(
    url: String,
    iceServersJson: String = "[]",
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val session = remember(iceServersJson) { arrayOfNulls<WhepSession>(1) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            SurfaceViewRenderer(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                keepScreenOn = true
                val created = WhepSession(ctx, this, iceServersJson)
                session[0] = created
                post { created.play(url) }
            }
        },
        update = { session[0]?.play(url) },
        onRelease = {
            session[0]?.release()
            session[0] = null
        },
    )

    DisposableEffect(lifecycleOwner, url, iceServersJson) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> Unit
                Lifecycle.Event.ON_RESUME -> session[0]?.play(url)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
