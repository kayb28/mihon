package eu.kanade.tachiyomi.network.interceptor

import org.json.JSONObject
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicLong

/**
 * State of one WebView fetch fallback request: it accumulates the response that the page-world
 * bridge script streams back and enforces the protocol rules on the Kotlin side.
 *
 * Kept in this file, free of any Android dependency, so the protocol itself can be unit-tested
 * on the JVM (see FetchSessionProtocolTest).
 *
 * Message contract (every message carries the session's one-time key "k"):
 *  - {"t":"head","k":...,"s":status,"m":statusText,"u":url,"h":{headers}}
 *  - {"t":"data","k":...,"b":<base64 chunk>}
 *  - {"t":"end","k":...}
 *  - {"t":"err","k":...,"m":reason}
 */
internal class FetchSession(
    private val maxBytes: Long,
    keyGenerator: () -> String = { UUID.randomUUID().toString() },
) {
    // One-time key stamped into every bridge message; a session only accepts its own
    // messages, so scripts from other frames/origins can't inject data into it
    val key: String = keyGenerator()

    @Volatile var failed: String? = null
        private set

    @Volatile var status: Int = 0
        private set

    @Volatile var statusText: String = ""
        private set

    @Volatile var fetchStarted: Boolean = false

    @Volatile var done: Boolean = false
        private set

    // Kotlin-side cap on the assembled response; the JS limit alone is not a security
    // boundary since any frame can post "data" messages over the legacy bridge
    private val receivedBytes = AtomicLong(0L)

    val headers = ConcurrentHashMap<String, String>()
    val latch = CountDownLatch(1)

    @Volatile private var buffer: ByteArray? = null
    private val bufferLock = Any()
    private val assembled = java.io.ByteArrayOutputStream()

    fun fail(reason: String) {
        if (failed == null) {
            failed = reason
            latch.countDown()
        }
    }

    fun onChallengeAbort(event: String) {
        if (!fetchStarted) fail("challenge $event")
    }

    /**
     * Processes one bridge message. Returns false when the message was rejected as not
     * belonging to this session (wrong key, message after finishing, oversized...).
     */
    fun onMessage(raw: String?): Boolean {
        val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return false
        if (obj.optString("k") != key) return false
        if (done || failed != null) return false
        when (obj.optString("t")) {
            "head" -> {
                if (status != 0 || receivedBytes.get() != 0L) return false
                status = obj.optInt("s")
                statusText = obj.optString("m")
                val h = obj.optJSONObject("h")
                if (h != null) {
                    for (k in h.keys()) headers[k.lowercase()] = h.optString(k)
                }
            }
            "data" -> {
                val bytes = runCatching { Base64.getDecoder().decode(obj.optString("b")) }.getOrNull()
                if (bytes == null) {
                    fail("chunk decode failed")
                    return false
                }
                if (receivedBytes.addAndGet(bytes.size.toLong()) > maxBytes) {
                    fail("response too large")
                    return false
                }
                synchronized(bufferLock) { assembled.write(bytes) }
            }
            "end" -> {
                if (status == 0) return false
                done = true
                synchronized(bufferLock) { buffer = assembled.toByteArray() }
                latch.countDown()
            }
            "err" -> fail(obj.optString("m").ifBlank { "fetch error" })
        }
        return true
    }

    /** The assembled response body; only complete after [done]. */
    fun body(): ByteArray? = buffer
}
