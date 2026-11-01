package eu.kanade.tachiyomi.ui.reader.viewer.webgpu

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * androidx.webgpu's queue reads ByteBuffers by (address, capacity): position and limit are
 * ignored (disassembly of the JNI in libwebgpu_c_bundled.so). [AndroidxWebGpuBufferOps.confine]
 * must hand it views whose capacity holds exactly the written bytes — a staging buffer that
 * outgrew its write used to make Dawn reject every uniform write and the reader drew black.
 */
class AndroidxWebGpuBufferOpsTest {

    @Test
    fun `an exact buffer at position zero is passed through`() {
        val buffer = ByteBuffer.allocateDirect(16)
        buffer.putInt(1).putInt(2).putInt(3).putInt(4)
        buffer.flip()

        AndroidxWebGpuBufferOps.confine(buffer) shouldBe buffer
    }

    @Test
    fun `a flipped slice of a larger staging buffer confines to the written bytes`() {
        val staging = ByteBuffer.allocateDirect(256).order(ByteOrder.nativeOrder())
        staging.putFloat(1.5f)
        staging.putFloat(-2f)
        staging.putFloat(0.25f)
        staging.putFloat(8f)
        staging.flip()

        val confined = AndroidxWebGpuBufferOps.confine(staging)
        confined.position() shouldBe 0
        confined.capacity() shouldBe 16
        confined.remaining() shouldBe 16
        confined.getFloat(0) shouldBe 1.5f
        confined.getFloat(4) shouldBe -2f
        confined.getFloat(8) shouldBe 0.25f
        confined.getFloat(12) shouldBe 8f
    }

    @Test
    fun `a buffer positioned past its start confines from the position onward`() {
        val buffer = ByteBuffer.allocateDirect(32)
        buffer.put(ByteArray(16))
        buffer.putInt(0x0A0B0C0D)
        buffer.flip()
        buffer.position(8)

        val confined = AndroidxWebGpuBufferOps.confine(buffer)
        confined.position() shouldBe 0
        confined.capacity() shouldBe 12
        confined.getInt(8) shouldBe 0x0A0B0C0D
    }
}
