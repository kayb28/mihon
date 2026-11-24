package eu.kanade.tachiyomi.ui.reader.setting

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

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
}
