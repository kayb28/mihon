package eu.kanade.tachiyomi.ui.reader.setting

/**
 * Pure decision layer for viewer selection (P1 separation: navigation × backend × degradation).
 *
 * JVM-testable by design: no Android or DI dependencies, so the safe-degradation rules can be
 * fuzz-tested without a device. [ReadingMode.toViewer] consumes these helpers; this file must
 * stay free of `android.*` imports.
 */
object ReadingModePolicy {

    /** Valid navigation flags of [ReadingMode] (DEFAULT=0 is never a stored value). */
    val VALID_FLAGS: Set<Int> = setOf(1, 2, 3, 4, 5)

    /**
     * Resolves a raw navigation flag coming from user data (P2: validate before acting).
     * Returns null when the value is null, negative, masked-off or out of range; the caller
     * degrades to the safest option (stock viewer) instead of throwing.
     */
    fun resolveFlag(raw: Int?): Int? = raw?.takeIf { it in VALID_FLAGS }

    /** Render backend axis, mirroring [eu.kanade.domain.base.BasePreferences.WebGpuViewerMode]. */
    enum class Backend { STOCK, HQ, EXPERIMENTAL }
}
