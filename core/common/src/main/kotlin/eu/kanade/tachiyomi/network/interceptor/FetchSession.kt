package eu.kanade.tachiyomi.network.interceptor

import org.json.JSONObject
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

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
 *
 * Sequencing is strict: a valid stream is head, zero or more data chunks, then end.
 * An invalid sequence carrying the session's own key (data/end before head, duplicated
 * head, head without a status in 100–599) fails the session right away, releasing the
 * latch so the caller gets a controlled error instead of waiting for the whole operation
 * timeout.
 */
internal class FetchSession(
    private val maxBytes: Long,
    keyGenerator: () -> String = { UUID.randomUUID().toString() },
) {
    // One-time key stamped into every bridge message. It correlates messages with their
    // session and keeps concurrent sessions isolated from each other; frames that don't
    // know the key can't inject into this session. It is NOT a security boundary against
    // the page itself: the delivery bridge lives in the page world on purpose, and the
    // page serves the response anyway.
    val key: String = keyGenerator()

    // First failure reason wins: CAS closes the write-write race when two threads fail the
    // session at once (the OkHttp thread on "evaluate failed: ..." while the main thread
    // processes a bridge message). An extra countDown would be harmless, but the recorded
    // reason would depend on thread scheduling.
    private val failure = AtomicReference<String?>(null)
    val failed: String?
        get() = failure.get()

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
        if (failure.compareAndSet(null, reason)) {
            latch.countDown()
        }
    }

    fun onChallengeAbort(event: String) {
        if (!fetchStarted) fail("challenge $event")
    }

    /**
     * Processes one bridge message. Returns false when the message was rejected — wrong
     * key, message after finishing, or an invalid sequence (which also fails the session
     * and releases the latch).
     */
    fun onMessage(raw: String?): Boolean {
        val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return false
        if (obj.optString("k") != key) return false
        if (done || failed != null) return false
        when (obj.optString("t")) {
            "head" -> {
                // Data only arrives after a head, so a non-zero status is exactly the
                // "head already seen" case; only real HTTP statuses (100–599) are accepted,
                // so a malformed head can't feed an invalid code into the response builder
                val s = obj.optInt("s")
                if (status != 0) {
                    fail("duplicate head")
                    return false
                }
                if (s == 0) {
                    fail("head without status")
                    return false
                }
                if (s !in 100..599) {
                    fail("head with invalid status")
                    return false
                }
                status = s
                statusText = obj.optString("m")
                val h = obj.optJSONObject("h")
                if (h != null) {
                    for (k in h.keys()) headers[k.lowercase()] = h.optString(k)
                }
            }
            "data" -> {
                if (status == 0) {
                    fail("data before head")
                    return false
                }
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
                if (status == 0) {
                    fail("end before head")
                    return false
                }
                // buffer first, then done: once done is visible, body() is guaranteed
                // non-null without relying on the latch's happens-before edge
                synchronized(bufferLock) { buffer = assembled.toByteArray() }
                done = true
                latch.countDown()
            }
            "err" -> fail(obj.optString("m").ifBlank { "fetch error" })
        }
        return true
    }

    /** The assembled response body; only complete after [done]. */
    fun body(): ByteArray? = buffer
}
