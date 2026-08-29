package com.domofon.app.ui

import android.net.Uri
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

private class VlcSession(context: android.content.Context, layout: VLCVideoLayout) {
    private val lib = LibVLC(
        context.applicationContext,
        arrayListOf(
            "--rtsp-tcp",
            "--network-caching=200",
            "--live-caching=200",
            "--clock-jitter=0",
            "--drop-late-frames",
            "--skip-frames",
            "--no-osd",
            "--no-stats",
        ),
    )
    val player: MediaPlayer = MediaPlayer(lib).also {
        it.attachViews(layout, null, false, false)
    }
    private var currentUrl: String? = null

    fun hasMedia(): Boolean = currentUrl != null

    fun play(url: String) {
        if (url.isBlank() || url == currentUrl) return
        currentUrl = url
        val media = Media(lib, Uri.parse(url))
        media.setHWDecoderEnabled(true, false)
        media.addOption(":rtsp-tcp")
        media.addOption(":network-caching=200")
        player.stop()
        player.media = media
        media.release()
        player.play()
    }

    fun pause() {
        runCatching { if (player.isPlaying) player.pause() }
    }

    fun resume() {
        runCatching {
            if (hasMedia() && !player.isPlaying) player.play()
        }
    }

    fun release() {
        runCatching {
            player.stop()
            player.detachViews()
            player.release()
            lib.release()
        }
        currentUrl = null
    }
}

@Composable
fun RtspPlayer(
    url: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onError: (String) -> Unit = {},
) {
    if (!enabled || url.isBlank()) return

    val lifecycleOwner = LocalLifecycleOwner.current
    val session = remember { arrayOfNulls<VlcSession>(1) }
    var initError by remember(url) { mutableStateOf<String?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            VLCVideoLayout(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                keepScreenOn = true
                runCatching {
                    val created = VlcSession(ctx, this)
                    session[0] = created
                    post { runCatching { created.play(url) }.onFailure { initError = it.message } }
                }.onFailure {
                    initError = it.message ?: "Не удалось запустить видеоплеер"
                    onError(initError!!)
                }
            }
        },
        update = { view ->
            if (initError == null) {
                runCatching { session[0]?.play(url) }
                    .onFailure {
                        initError = it.message
                        onError(it.message ?: "Ошибка воспроизведения")
                    }
            }
        },
        onRelease = {
            session[0]?.release()
            session[0] = null
        },
    )

    DisposableEffect(lifecycleOwner, url) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> session[0]?.pause()
                Lifecycle.Event.ON_RESUME -> session[0]?.resume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
