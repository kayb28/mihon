package eu.kanade.tachiyomi.ui.reader.viewer

import eu.kanade.tachiyomi.ui.reader.setting.ReadingModePolicy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** JVM tests for the backend degradation ladder and the page-failure circuit breaker (T5). */
class ViewerBackendPolicyTest {

    // --- degrade: EXPERIMENTAL→HQ→STOCK→null ---

    @Test
    fun `degradation ladder steps down one level at a time`() {
        assertEquals(
            ReadingModePolicy.Backend.HQ,
            ViewerBackendPolicy.degrade(ReadingModePolicy.Backend.EXPERIMENTAL),
        )
        assertEquals(
            ReadingModePolicy.Backend.STOCK,
            ViewerBackendPolicy.degrade(ReadingModePolicy.Backend.HQ),
        )
        assertNull(ViewerBackendPolicy.degrade(ReadingModePolicy.Backend.STOCK), "STOCK is the floor")
    }

    // --- circuit breaker: threshold semantics ---

    @Test
    fun `breaker trips exactly at the threshold of consecutive failures`() {
        val breaker = ViewerBackendPolicy.CircuitBreaker(threshold = 3)
        assertFalse(breaker.onPageFailure())
        assertFalse(breaker.onPageFailure())
        assertTrue(breaker.onPageFailure(), "third consecutive failure trips")
        assertTrue(breaker.isTripped())
    }

    @Test
    fun `a tripped breaker stays tripped until reset`() {
        val breaker = ViewerBackendPolicy.CircuitBreaker(threshold = 3)
        repeat(3) { breaker.onPageFailure() }
        assertTrue(breaker.onPageFailure(), "already tripped keeps reporting true")
        breaker.reset()
        assertFalse(breaker.isTripped())
    }

    @Test
    fun `a successful page resets the count (isolated failures never degrade)`() {
        val breaker = ViewerBackendPolicy.CircuitBreaker(threshold = 3)
        breaker.onPageFailure()
        breaker.onPageFailure()
        breaker.onPageSuccess()
        assertFalse(breaker.onPageFailure(), "count restarted, not tripped")
        assertFalse(breaker.onPageFailure())
        assertTrue(breaker.onPageFailure())
    }

    @Test
    fun `fuzz breaker never trips before the threshold`() {
        val rng = kotlin.random.Random(0xBADC0DE)
        repeat(1_000) {
            val breaker = ViewerBackendPolicy.CircuitBreaker(threshold = 3)
            var consecutive = 0
            repeat(rng.nextInt(1, 20)) {
                val success = rng.nextBoolean()
                if (success) {
                    breaker.onPageSuccess()
                    consecutive = 0
                } else {
                    consecutive++
                    assertEquals(consecutive >= 3, breaker.onPageFailure())
                }
                assertTrue(breaker.isTripped() == (consecutive >= 3))
            }
        }
    }
}
