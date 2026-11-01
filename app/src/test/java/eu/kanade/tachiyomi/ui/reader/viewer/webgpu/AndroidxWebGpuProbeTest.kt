package eu.kanade.tachiyomi.ui.reader.viewer.webgpu

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

/**
 * The probe must answer "false" — never throw — when the androidx.webgpu runtime cannot run.
 *
 * On the JVM the androidx.webgpu classes exist (testImplementation) but libwebgpu_c_bundled does
 * not, so WebGpuUtils.initLibrary() throws UnsatisfiedLinkError. That error is a LinkageError,
 * not an Exception: with the old `catch (e: Exception)` it escaped probeRuntime() and crashed
 * the reader the moment the experimental viewer was enabled (Mihon crash log on e561462, POCO
 * peridot / Android 16).
 */
class AndroidxWebGpuProbeTest {

    @Test
    fun `probe reports false instead of throwing when the native library cannot load`() = runBlocking<Unit> {
        AndroidxWebGpuProbe.probe() shouldBe false
    }

    @Test
    fun `a failed probe is cached and keeps answering false without re-throwing`() = runBlocking<Unit> {
        AndroidxWebGpuProbe.probe() shouldBe false
        AndroidxWebGpuProbe.probe() shouldBe false
    }
}
