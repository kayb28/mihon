package eu.kanade.tachiyomi.ui.reader.viewer.webgpu

import java.nio.ByteBuffer

/**
 * Confinement for the ByteBuffers handed to androidx.webgpu's queue (the black-screen fix).
 *
 * The androidx.webgpu JNI reads its data sources with `GetDirectBufferAddress` +
 * `GetDirectBufferCapacity` only — verified by disassembling
 * `Java_androidx_webgpu_GPUQueue_writeBuffer` and the `writeTexture` staging helper in
 * `libwebgpu_c_bundled.so`: `position` and `limit` are ignored and the copy spans the buffer's
 * whole **capacity**. A grow-only staging buffer therefore must not be passed as-is: Dawn would
 * reject the write (its capacity outgrew the uniform buffer's size) and the frame would draw
 * with zeroed uniforms — a degenerate quad over the black clear color. [confine] folds
 * position/limit into a fresh view whose base is the written region and whose capacity equals
 * the remaining bytes, making the capacity-based read see exactly what was written.
 */
internal object AndroidxWebGpuBufferOps {

    /**
     * A view of [buffer] holding exactly its remaining bytes (the same instance when it is
     * already confined: at position 0 with capacity == remaining).
     */
    fun confine(buffer: ByteBuffer): ByteBuffer {
        if (buffer.position() == 0 && buffer.remaining() == buffer.capacity()) return buffer
        return buffer.slice().order(buffer.order())
    }
}
