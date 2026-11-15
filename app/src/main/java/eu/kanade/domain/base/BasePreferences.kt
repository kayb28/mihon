package eu.kanade.domain.base

import android.content.Context
import dev.icerock.moko.resources.StringResource
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum
import tachiyomi.i18n.MR

@Inject
@SingleIn(AppScope::class)
class BasePreferences(
    val context: Context,
    preferenceStore: PreferenceStore,
) {

    val downloadedOnly: Preference<Boolean> = preferenceStore.getBoolean(
        Preference.appStateKey("pref_downloaded_only"),
        false,
    )

    val incognitoMode: Preference<Boolean> = preferenceStore.getBoolean(Preference.appStateKey("incognito_mode"), false)

    val extensionInstaller: ExtensionInstallerPreference = ExtensionInstallerPreference(context, preferenceStore)

    val shownOnboardingFlow: Preference<Boolean> = preferenceStore.getBoolean(
        Preference.appStateKey("onboarding_complete"),
        false,
    )

    enum class ExtensionInstaller(val titleRes: StringResource, val requiresSystemPermission: Boolean) {
        LEGACY(MR.strings.ext_installer_legacy, true),
        PACKAGEINSTALLER(MR.strings.ext_installer_packageinstaller, true),
        SHIZUKU(MR.strings.ext_installer_shizuku, false),
        PRIVATE(MR.strings.ext_installer_private, false),
    }

    /**
     * Unified viewer mode for the reader.
     * STOCK = stock pager/webtoon viewers.
     * HQ = ca.mpreg WebGPU high-quality viewer.
     * EXPERIMENTAL = androidx.webgpu viewer (falls back on device failure).
     *
     * Migrates from the previous independent flags on first read of the new key.
     * Old keys are left in place and still readable.
     */
    enum class WebGpuViewerMode {
        STOCK,
        HQ,
        EXPERIMENTAL,
    }

    val webGpuViewerMode: Preference<WebGpuViewerMode> = preferenceStore.getEnum(
        "pref_webgpu_viewer_mode_key",
        when {
            preferenceStore.getBoolean("pref_androidx_webgpu_viewer_key", false).get() ->
                WebGpuViewerMode.EXPERIMENTAL
            preferenceStore.getBoolean("pref_high_quality_renderer_key", false).get() ->
                WebGpuViewerMode.HQ
            else -> WebGpuViewerMode.STOCK
        },
    )

    // Kept for migration and any remaining direct reads; do not remove in this change.
    val highQualityRenderer: Preference<Boolean> = preferenceStore.getBoolean("pref_high_quality_renderer_key", false)

    /**
     * WebGPU migration experiment: paged modes build [AndroidxWebGpuViewer] (androidx.webgpu)
     * instead of the ca.mpreg HQ viewer. Off by default; the HQ path is untouched while this is
     * false. When the androidx.webgpu runtime cannot draw on the device, the reader falls back to
     * the viewer below for the rest of the session. Remove once the migration owns the paged reader.
     */
    val androidxWebGpuViewer: Preference<Boolean> = preferenceStore.getBoolean("pref_androidx_webgpu_viewer_key", false)

    val installationId: Preference<String> = preferenceStore.getString(Preference.appStateKey("installation_id"), "")

    val donationCampaignShown: Preference<Boolean> = preferenceStore.getBoolean(
        Preference.appStateKey("donation_campaign_shown"),
        false,
    )

    val skippedAppUpdate: Preference<String> = preferenceStore.getString(
        Preference.appStateKey("skipped_app_update"),
        "",
    )
}
