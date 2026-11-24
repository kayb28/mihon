package eu.kanade.domain.base

import tachiyomi.core.common.preference.Preference

/**
 * One-shot migration from the legacy independent viewer flags to the unified
 * [BasePreferences.WebGpuViewerMode] preference (fix D2).
 *
 * Pure and JVM-testable: operates only on [Preference] handles and booleans.
 */
object WebGpuViewerModeMigration {

    /** Unified key; from the one-shot migration on, the single source of truth. */
    const val NEW_KEY = "pref_webgpu_viewer_mode_key"

    /** Legacy key that selected the androidx.webgpu experimental viewer. */
    const val LEGACY_EXPERIMENTAL_KEY = "pref_androidx_webgpu_viewer_key"

    /** Legacy key that selected the ca.mpreg HQ viewer. */
    const val LEGACY_HQ_KEY = "pref_high_quality_renderer_key"

    /**
     * If the unified key has no stored value (first launch after this build installs), computes
     * the mode ONCE from the legacy flags and WRITES it, so later launches no longer recompute
     * the default from the legacy keys. If the unified key is already set — including a corrupt
     * value, which the enum deserializer resolves to STOCK — nothing happens: the legacy keys
     * become read-only leftovers and never govern behaviour again. Never throws and never
     * overwrites a user choice.
     */
    fun migrateOnce(
        newPref: Preference<BasePreferences.WebGpuViewerMode>,
        legacyExperimental: Boolean,
        legacyHq: Boolean,
    ) {
        if (newPref.isSet()) return
        newPref.set(
            when {
                legacyExperimental -> BasePreferences.WebGpuViewerMode.EXPERIMENTAL
                legacyHq -> BasePreferences.WebGpuViewerMode.HQ
                else -> BasePreferences.WebGpuViewerMode.STOCK
            },
        )
    }
}
