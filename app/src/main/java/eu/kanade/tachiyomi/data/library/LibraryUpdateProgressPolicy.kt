package eu.kanade.tachiyomi.data.library

/**
 * T-D (PROMPT 12): the library-update progress notification must show the count
 * («X de Y títulos» / «X títulos») in addition to the percentage, so the user can verify
 * the scope of the update.
 *
 * GREEN state (T-D): countLabel decides when and what count the notification shows.
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

    /** GREEN T-D: show «X de Y títulos» whenever there is work; current clamped into 0..total. */
    fun countLabel(current: Int, total: Int): Count {
        if (total <= 0) return Count(shown = false, current = 0, total = 0)
        return Count(
            shown = true,
            current = current.coerceIn(0, total),
            total = total,
        )
    }
}
