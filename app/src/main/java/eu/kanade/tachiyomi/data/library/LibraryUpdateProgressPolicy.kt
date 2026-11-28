package eu.kanade.tachiyomi.data.library

/**
 * T-D (PROMPT 12): the library-update progress notification must show the count
 * («X de Y títulos» / «X títulos») in addition to the percentage, so the user can verify
 * the scope of the update.
 *
 * RED state (evidence/t-d-red): the notification shows only the percentage (the bug).
 */
object LibraryUpdateProgressPolicy {

    /**
     * [shown] — whether the notification must display a count;
     * [current]/[total] — the numbers to render, with current clamped into 0..total.
     */
    data class Count(
        val shown: Boolean,
        val current: Int,
        val total: Int,
    )

    /** RED T-D (evidence/t-d-red): the count is still never shown. */
    fun countLabel(current: Int, total: Int): Count = Count(shown = false, current = current, total = total)
}
