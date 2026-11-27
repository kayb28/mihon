package eu.kanade.tachiyomi.network

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import mihon.core.metro.IsDebugBuild
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

@Inject
@SingleIn(AppScope::class)
class NetworkPreferences(
    preferenceStore: PreferenceStore,
    @IsDebugBuild isDebugBuild: Boolean,
) {

    val redactSensitiveHeaders: Preference<Boolean> = preferenceStore.getBoolean(
        "redact_sensitive_headers",
        true,
    )

    val verboseLogging: Preference<Boolean> = preferenceStore.getBoolean(
        "verbose_logging",
        isDebugBuild,
    )

    val dohProvider: Preference<Int> = preferenceStore.getInt("doh_provider", -1)

    val webviewFallbackEnabled: Preference<Boolean> = preferenceStore.getBoolean(
        "webview_fallback_enabled",
        true,
    )

    /**
     * W4: per-host cooldown after a failed WebView attempt, in minutes. Conservative
     * default of 10 minutes (the audited baseline); raise it if a problematic host keeps
     * spawning WebView attempts, lower it when iterating on a source that is being fixed.
     */
    val webviewFallbackCooldownMinutes: Preference<Int> = preferenceStore.getInt(
        "webview_fallback_cooldown_minutes",
        10,
    )

    val defaultUserAgent: Preference<String> = preferenceStore.getString(
        "default_user_agent",
        "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Mobile Safari/537.36",
    )
}
