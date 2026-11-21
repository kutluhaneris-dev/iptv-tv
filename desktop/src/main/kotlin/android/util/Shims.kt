package android.util

// Stand-ins for the handful of android.util classes the shared data code (app/.../data) uses,
// so the desktop build compiles those files unchanged.

typealias JsonReader = com.google.gson.stream.JsonReader
typealias JsonToken = com.google.gson.stream.JsonToken

object Base64 {
    const val DEFAULT = 0

    fun decode(str: String, @Suppress("UNUSED_PARAMETER") flags: Int): ByteArray =
        java.util.Base64.getMimeDecoder().decode(str)
}

object Xml {
    fun newPullParser(): org.xmlpull.v1.XmlPullParser = org.kxml2.io.KXmlParser()
}

object Log {
    fun i(tag: String, msg: String): Int {
        println("$tag: $msg")
        return 0
    }
}
