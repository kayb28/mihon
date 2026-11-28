package eu.kanade.tachiyomi.data.library

/**
 * T-D (PROMPT 12): the library-update progress notification must show the count
 * («X de Y títulos» / «X títulos») in addition to the percentage, so the user can verify
 * the scope of the update.
 *
 * RED state: countLabel reports shown=false — the count is not displayed yet (bug).
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

    /** RED T-D: still never shows the count. */
    fun countLabel(current: Int, total: Int): Count = Count(shown = false, current = current, total = total)
}
