package dev.kutluhan.iptv.desktop

import javax.swing.SwingUtilities
import kotlin.system.exitProcess

/** CI check: plays a file through the same pipeline as the app and fails if no picture arrives. */
fun main(args: Array<String>) {
    val factory = Vlc.factory ?: run {
        println("FRAMECHECK: VLC not found")
        exitProcess(2)
    }
    lateinit var player: VideoPlayer
    SwingUtilities.invokeAndWait { player = VideoPlayer(factory) }
    SwingUtilities.invokeAndWait { player.play(args[0], "VLC/3.0.20 LibVLC/3.0.20", live = true) }
    Thread.sleep(8000)
    var ok = false
    SwingUtilities.invokeAndWait {
        val f = player.frame
        println("FRAMECHECK: state=${player.state} rendered=${player.renderedFrames} frame=${f?.width}x${f?.height}")
        if (f != null) {
            val px = IntArray(1)
            f.readPixels(px, startX = f.width / 4, startY = f.height / 4, width = 1, height = 1)
            println("FRAMECHECK: pixel=${Integer.toHexString(px[0])}")
        }
        ok = f != null && player.renderedFrames > 10
    }
    println(if (ok) "FRAMECHECK: OK" else "FRAMECHECK: NO PICTURE")
    exitProcess(if (ok) 0 else 1)
}
