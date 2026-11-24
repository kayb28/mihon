package eu.kanade.tachiyomi.ui.reader.setting

import eu.kanade.tachiyomi.ui.reader.setting.ReadingModePolicy.Backend
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Truth-table matrix (T7): 3 viewer modes × 6 reading-mode values (the 5 valid flags plus one
 * corrupt value) × 2 session-fallback states (no override / override to HQ) = 36 cases, over
 * the single pure backend decision [ReadingModePolicy.selectBackend].
 *
 * Behaviour contract: zero change for STOCK/HQ users; the session fallback only degrades the
 * EXPERIMENTAL axis; a corrupt reading-mode value always lands on the stock path (D1).
 */
class ReadingModeSelectionTruthTableTest {

    private val modes = listOf(Backend.STOCK, Backend.HQ, Backend.EXPERIMENTAL)
    private val readings = listOf(1, 2, 3, 4, 5, 7) // 7 = corrupt (not a valid flag)
    private val overrides = listOf<Backend?>(null, Backend.HQ)

    @Test
    fun `truth table covers exactly 36 cases`() {
        assertEquals(3 * 6 * 2, modes.size * readings.size * overrides.size)
    }

    @Test
    fun `truth table - corrupt reading mode always degrades to STOCK`() {
        for (mode in modes) {
            for (override in overrides) {
                assertEquals(
                    Backend.STOCK,
                    ReadingModePolicy.selectBackend(mode, navigationFlag = 7, sessionOverride = override),
                    "corrupt flag + mode=$mode + override=$override must land on STOCK",
                )
            }
        }
    }

    @Test
    fun `truth table - session override wins over preference for valid flags`() {
        for (mode in modes) {
            for (flag in 1..5) {
                assertEquals(
                    Backend.HQ,
                    ReadingModePolicy.selectBackend(mode, navigationFlag = flag, sessionOverride = Backend.HQ),
                    "override must pin HQ for mode=$mode flag=$flag",
                )
            }
        }
    }

    @Test
    fun `truth table - without override the preference decides for valid flags`() {
        for (mode in modes) {
            for (flag in 1..5) {
                assertEquals(
                    mode,
                    ReadingModePolicy.selectBackend(mode, navigationFlag = flag, sessionOverride = null),
                    "no override keeps mode=$mode for flag=$flag",
                )
            }
        }
    }

    @Test
    fun `truth table - every flag resolves to its navigation entry (semantics intact)`() {
        // Flag → ReadingMode mapping the viewers build on; fallback semantics untouched.
        assertEquals(ReadingMode.LEFT_TO_RIGHT, ReadingMode.fromPreference(1))
        assertEquals(ReadingMode.RIGHT_TO_LEFT, ReadingMode.fromPreference(2))
        assertEquals(ReadingMode.VERTICAL, ReadingMode.fromPreference(3))
        assertEquals(ReadingMode.WEBTOON, ReadingMode.fromPreference(4))
        assertEquals(ReadingMode.CONTINUOUS_VERTICAL, ReadingMode.fromPreference(5))
        assertEquals(ReadingMode.DEFAULT, ReadingMode.fromPreference(null))
        assertEquals(ReadingMode.DEFAULT, ReadingMode.fromPreference(0))
        assertEquals(ReadingMode.DEFAULT, ReadingMode.fromPreference(7))
    }

    @Test
    fun `truth table - paged vs continuous classification unchanged`() {
        // (a) navigation-model axis: L2R/R2L/VERTICAL are paged, WEBTOON/CONTINUOUS_VERTICAL are continuous.
        assertTrue(ReadingMode.isPagerType(1) && ReadingMode.isPagerType(2) && ReadingMode.isPagerType(3))
        assertTrue(!ReadingMode.isPagerType(4) && !ReadingMode.isPagerType(5))
    }
}
