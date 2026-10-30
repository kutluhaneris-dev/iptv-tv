package dev.kutluhan.iptv.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import java.nio.ByteBuffer
import javax.swing.SwingUtilities

/** libVLC, found in the installed VLC app. Null when VLC is not installed. */
object Vlc {
    val factory: MediaPlayerFactory? by lazy {
        try {
            if (!NativeDiscovery().discover()) return@lazy null
            val verbose = System.getenv("IPTV_VLC_VERBOSE") != null
            MediaPlayerFactory(if (verbose) "-vv" else "--quiet", "--no-video-title-show", "--no-snapshot-preview", "--no-osd")
        } catch (e: Throwable) {
            System.err.println("VLC yüklenemedi: $e")
            null
        }
    }

    val available: Boolean get() = factory != null

    const val DOWNLOAD_URL = "https://www.videolan.org/vlc/"
}

enum class PlayState { IDLE, OPENING, PLAYING, PAUSED, ENDED, ERROR }

data class Track(val id: Int, val label: String)

/**
 * One libVLC player whose frames are copied into a Compose image, so video can sit anywhere in
 * the UI (the channel preview, the full player) with overlays drawn on top.
 */
class VideoPlayer(factory: MediaPlayerFactory, initialVolume: Int = 100) {
    var frame by mutableStateOf<ImageBitmap?>(null)
        private set
    var state by mutableStateOf(PlayState.IDLE)
        private set
    var buffering by mutableStateOf(0f)
        private set
    var time by mutableStateOf(0L)
        private set
    var length by mutableStateOf(0L)
        private set
    var audioTracks by mutableStateOf<List<Track>>(emptyList())
        private set
    var audioTrack by mutableStateOf(-1)
        private set
    var subtitleTracks by mutableStateOf<List<Track>>(emptyList())
        private set
    var subtitleTrack by mutableStateOf(-1)
        private set
    var volume by mutableStateOf(initialVolume)
        private set
    var muted by mutableStateOf(false)
        private set

    /** What is loaded now, so the preview and the full player can hand one stream between them. */
    var url by mutableStateOf<String?>(null)
        private set

    private val player: EmbeddedMediaPlayer = factory.mediaPlayers().newEmbeddedMediaPlayer()
    private var info: ImageInfo? = null
    private val shown = ArrayDeque<Bitmap>()
    private val framePending = java.util.concurrent.atomic.AtomicBoolean(false)

    /** Frames libVLC has handed over, for diagnosing a stream with sound but no picture. */
    @Volatile
    var renderedFrames = 0
        private set
    private var released = false

    init {
        val formatCallback = object : BufferFormatCallback {
            override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
                info = ImageInfo(sourceWidth, sourceHeight, ColorType.BGRA_8888, ColorAlphaType.OPAQUE)
                return RV32BufferFormat(sourceWidth, sourceHeight)
            }

            override fun allocatedBuffers(buffers: Array<ByteBuffer>) = Unit
        }
        val renderCallback = RenderCallback { _, buffers, _ ->
            val i = info ?: return@RenderCallback
            renderedFrames++
            // Drop this frame if the UI has not drawn the previous one yet, so slow drawing can't build a backlog.
            if (!framePending.compareAndSet(false, true)) return@RenderCallback
            val buf = buffers[0]
            // Each frame gets its own pixels: libVLC reuses its buffer, and Skia keeps the image alive.
            val copy = ByteArray(buf.capacity())
            buf.rewind()
            buf.get(copy)
            buf.rewind()
            SwingUtilities.invokeLater {
                try {
                    if (released) return@invokeLater
                    val bmp = Bitmap()
                    bmp.installPixels(i, copy, i.minRowBytes)
                    bmp.setImmutable()
                    frame = bmp.asComposeImageBitmap()
                    shown.addLast(bmp)
                    // Skia keeps pixels alive while an image still draws them, so older handles can go.
                    while (shown.size > 3) shown.removeFirst().close()
                } finally {
                    framePending.set(false)
                }
            }
        }
        player.videoSurface().set(factory.videoSurfaces().newVideoSurface(formatCallback, renderCallback, true))
        player.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun opening(mediaPlayer: MediaPlayer) = ui { state = PlayState.OPENING }
            override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) = ui { buffering = newCache }
            override fun playing(mediaPlayer: MediaPlayer) = ui { state = PlayState.PLAYING }
            override fun paused(mediaPlayer: MediaPlayer) = ui { state = PlayState.PAUSED }
            override fun finished(mediaPlayer: MediaPlayer) = ui { state = PlayState.ENDED }
            override fun error(mediaPlayer: MediaPlayer) = ui { state = PlayState.ERROR }
            override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) = ui { time = newTime }
            override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) = ui { length = newLength }
            override fun elementaryStreamAdded(mediaPlayer: MediaPlayer, type: uk.co.caprica.vlcj.media.TrackType, id: Int) =
                refreshTracksLater()
            override fun elementaryStreamDeleted(mediaPlayer: MediaPlayer, type: uk.co.caprica.vlcj.media.TrackType, id: Int) =
                refreshTracksLater()
            override fun elementaryStreamSelected(mediaPlayer: MediaPlayer, type: uk.co.caprica.vlcj.media.TrackType, id: Int) =
                refreshTracksLater()
        })
    }

    private fun ui(block: () -> Unit) = SwingUtilities.invokeLater { if (!released) block() }

    /** libVLC must not be called back from its own event thread, so track lists are read on the UI thread. */
    private fun refreshTracksLater() = ui { refreshTracks() }

    private fun refreshTracks() {
        audioTracks = player.audio().trackDescriptions().filter { it.id() >= 0 }.map { Track(it.id(), it.description()) }
        audioTrack = player.audio().track()
        subtitleTracks = player.subpictures().trackDescriptions().filter { it.id() >= 0 }.map { Track(it.id(), it.description()) }
        subtitleTrack = player.subpictures().track()
    }

    fun play(url: String, userAgent: String, live: Boolean) {
        this.url = url
        time = 0
        length = 0
        buffering = 0f
        audioTracks = emptyList()
        subtitleTracks = emptyList()
        state = PlayState.OPENING
        val options = buildList {
            add(":http-user-agent=$userAgent")
            add(":http-reconnect")
            // Hardware decoding hands out GPU pictures that some streams never convert to a visible frame
            // in this render path; software decoding is reliable and fast enough on current Macs and PCs.
            add(":avcodec-hw=none")
            add(":network-caching=${if (live) 2000 else 3000}")
        }
        player.media().play(url, *options.toTypedArray())
        player.audio().setVolume(volume)
        player.audio().setMute(muted)
    }

    /** Codec, size and frame rate of the picture libVLC found, for the D info panel and CI. */
    fun describeVideo(): String = try {
        val tracks = player.media().info().videoTracks()
        if (tracks.isEmpty()) "video parçası yok" else tracks.joinToString { t ->
            "${t.codecName()} ${t.width()}x${t.height()} ${if (t.frameRateBase() > 0) t.frameRate() / t.frameRateBase() else 0} fps"
        }
    } catch (e: Throwable) {
        "?"
    }

    fun togglePause() {
        when (state) {
            PlayState.PLAYING -> player.controls().setPause(true)
            PlayState.PAUSED -> player.controls().setPause(false)
            else -> Unit
        }
    }

    val seekable: Boolean get() = length > 0

    fun seekBy(ms: Long) {
        if (!seekable) return
        seekTo((time + ms).coerceIn(0, length))
    }

    fun seekTo(ms: Long) {
        if (!seekable) return
        player.controls().setTime(ms)
        time = ms
    }

    fun changeVolume(value: Int) {
        volume = value.coerceIn(0, 150)
        player.audio().setVolume(volume)
        if (muted && volume > 0) toggleMute()
    }

    fun toggleMute() {
        muted = !muted
        player.audio().setMute(muted)
    }

    fun selectAudio(id: Int) {
        player.audio().setTrack(id)
        refreshTracks()
    }

    fun selectSubtitle(id: Int) {
        player.subpictures().setTrack(id)
        refreshTracks()
    }

    fun stop() {
        url = null
        player.controls().stop()
        state = PlayState.IDLE
        frame = null
    }

    fun release() {
        if (released) return
        released = true
        val p = player
        // Releasing blocks until libVLC's threads finish, which must not happen on the UI thread.
        Thread {
            runCatching { p.controls().stop() }
            runCatching { p.release() }
        }.start()
        shown.forEach { it.close() }
        shown.clear()
        frame = null
    }
}
