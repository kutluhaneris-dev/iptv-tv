package dev.kutluhan.iptv.data

import android.util.JsonReader
import android.util.JsonToken
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

const val DEFAULT_USER_AGENT = "VLC/3.0.20 LibVLC/3.0.20"

object Http {
    @Volatile
    var userAgent: String = DEFAULT_USER_AGENT

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", userAgent)
                        .build()
                )
            }
            .build()
    }

    /** Opens [url] and hands the (transparently un-gzipped) body to [block]. */
    fun <T> open(url: String, block: (InputStream) -> T): T {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Sunucu hatası: HTTP ${response.code}")
            val body = response.body ?: throw IOException("Sunucudan boş yanıt geldi")
            val input = BufferedInputStream(body.byteStream(), 64 * 1024)
            input.mark(2)
            val b1 = input.read()
            val b2 = input.read()
            input.reset()
            val stream = if (b1 == 0x1f && b2 == 0x8b) GZIPInputStream(input, 64 * 1024) else input
            return block(stream)
        }
    }

    fun text(url: String): String = open(url) { it.readBytes().toString(Charsets.UTF_8) }
}

/**
 * Streams a JSON array of flat objects, giving each object's scalar fields as strings.
 * Xtream servers are inconsistent about numbers vs strings, so everything becomes a string.
 * Nested arrays/objects are skipped. A non-array response yields nothing.
 */
fun readJsonObjects(input: InputStream, onObject: (Map<String, String?>) -> Unit) {
    val reader = JsonReader(InputStreamReader(input, Charsets.UTF_8))
    reader.isLenient = true
    if (reader.peek() != JsonToken.BEGIN_ARRAY) return
    reader.beginArray()
    while (reader.hasNext()) {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            reader.skipValue()
            continue
        }
        val fields = HashMap<String, String?>()
        reader.beginObject()
        while (reader.hasNext()) {
            val key = reader.nextName()
            when (reader.peek()) {
                JsonToken.STRING, JsonToken.NUMBER -> fields[key] = reader.nextString()
                JsonToken.BOOLEAN -> fields[key] = reader.nextBoolean().toString()
                JsonToken.NULL -> {
                    reader.nextNull()
                    fields[key] = null
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        onObject(fields)
    }
    reader.endArray()
}

internal fun String?.orNullIfBlank(): String? = if (this.isNullOrBlank()) null else this
