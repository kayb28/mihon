package eu.kanade.tachiyomi.ui.reader.setting

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.random.Random
import org.junit.jupiter.api.Test

/**
 * JVM fuzz tests for the safe-degradation rules of [ReadingModePolicy] (fix D1).
 * Corrupt user-data-derived values must resolve to a safe degrade, never throw.
 */
class ReadingModePolicyTest {

    // --- resolveFlag: valid values pass through ---

    @Test
    fun `valid flags resolve to themselves`() {
        ReadingModePolicy.VALID_FLAGS.forEach { flag ->
            assertEquals(flag, ReadingModePolicy.resolveFlag(flag))
        }
    }

    @Test
    fun `null flag degrades to null instead of crashing`() {
        assertNull(ReadingModePolicy.resolveFlag(null))
    }

    @Test
    fun `flag zero (DEFAULT) is not a stored value and resolves to null`() {
        assertNull(ReadingModePolicy.resolveFlag(0))
    }

    @Test
    fun `masked-off garbage degrades to null`() {
        assertNull(ReadingModePolicy.resolveFlag(0x00000001 or 0x10), "17 is not a valid flag")
    }

    // --- resolveFlag: JVM fuzz over hostile values ---

    @Test
    fun `fuzz hostile values never throw and never validate garbage`() {
        val hostile = listOf(
            -1, -2, Int.MIN_VALUE, Int.MAX_VALUE, Int.MAX_VALUE - 1, 6, 7, 8,
            0x00000007 + 1, -0x00000001, 100, 255, 1 shl 30,
        )
        hostile.forEach { value ->
            val resolved = ReadingModePolicy.resolveFlag(value)
            if (value in ReadingModePolicy.VALID_FLAGS) {
                assertEquals(value, resolved)
            } else {
                assertNull(resolved, "value $value must not validate")
            }
        }
    }

    @Test
    fun `fuzz random values never throw`() {
        val rng = Random(0x5EED)
        repeat(10_000) {
            val value = rng.nextInt()
            val resolved = ReadingModePolicy.resolveFlag(value)
            assertTrue(resolved == null || resolved in ReadingModePolicy.VALID_FLAGS)
        }
    }

    // --- backend: degradation policy (P3) ---

    @Test
    fun `session fallback degrades EXPERIMENTAL to HQ`() {
        assertEquals(
            ReadingModePolicy.Backend.HQ,
            ReadingModePolicy.backend(ReadingModePolicy.Backend.EXPERIMENTAL, sessionFallback = true),
        )
    }

    @Test
    fun `no fallback keeps EXPERIMENTAL`() {
        assertEquals(
            ReadingModePolicy.Backend.EXPERIMENTAL,
            ReadingModePolicy.backend(ReadingModePolicy.Backend.EXPERIMENTAL, sessionFallback = false),
        )
    }

    @Test
    fun `STOCK and HQ are unaffected by fallback`() {
        assertEquals(
            ReadingModePolicy.Backend.STOCK,
            ReadingModePolicy.backend(ReadingModePolicy.Backend.STOCK, sessionFallback = true),
        )
        assertEquals(
            ReadingModePolicy.Backend.HQ,
            ReadingModePolicy.backend(ReadingModePolicy.Backend.HQ, sessionFallback = true),
        )
    }

    @Test
    fun `fuzz fallback combinations never throw`() {
        val rng = Random(0xF00D)
        val backends = ReadingModePolicy.Backend.entries
        repeat(1_000) {
            val backend = backends[rng.nextInt(backends.size)]
            val fallback = rng.nextBoolean()
            val resolved = ReadingModePolicy.backend(backend, fallback)
            assertTrue(
                resolved != ReadingModePolicy.Backend.EXPERIMENTAL || (!fallback && backend == ReadingModePolicy.Backend.EXPERIMENTAL),
            )
        }
    }
}
