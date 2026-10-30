package dev.kutluhan.iptv.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.skia.Bitmap
import javax.swing.SwingUtilities
import kotlin.system.exitProcess

/**
 * CI check: plays each file through the same pipeline as the app, then draws the frame with the same
 * Compose Image the screens use, and fails if no picture arrives or the drawn picture is blank.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main(args: Array<String>) {
    val factory = Vlc.factory ?: run {
        println("FRAMECHECK: VLC not found")
        exitProcess(2)
    }
    var allOk = true
    for (file in args) {
        lateinit var player: VideoPlayer
        SwingUtilities.invokeAndWait { player = VideoPlayer(factory) }
        SwingUtilities.invokeAndWait { player.play(file, "VLC/3.0.20 LibVLC/3.0.20", live = true) }
        Thread.sleep(9000)
        var ok = false
        SwingUtilities.invokeAndWait {
            val f = player.frame
            println("FRAMECHECK[$file]: state=${player.state} rendered=${player.renderedFrames} frame=${f?.width}x${f?.height} video=${player.describeVideo()}")
            if (f != null && player.renderedFrames > 10) {
                val scene = ImageComposeScene(320, 180) {
                    Image(f, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                }
                val image = scene.render()
                val bmp = Bitmap.makeFromImage(image)
                val colors = HashSet<Int>()
                for (x in 0 until 320 step 16) for (y in 0 until 180 step 12) colors += bmp.getColor(x, y)
                println("FRAMECHECK[$file]: drawn distinct colors=${colors.size}")
                ok = colors.size > 4
                scene.close()
            }
        }
        println("FRAMECHECK[$file]: " + if (ok) "OK" else "NO PICTURE")
        allOk = allOk && ok
        SwingUtilities.invokeAndWait { player.release() }
    }
    println(if (allOk) "FRAMECHECK: ALL OK" else "FRAMECHECK: FAILED")
    exitProcess(if (allOk) 0 else 1)
}
