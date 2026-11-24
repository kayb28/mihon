package eu.kanade.tachiyomi.ui.reader.setting

import android.util.Log
import androidx.compose.ui.graphics.vector.ImageVector
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.viewer.Viewer
import eu.kanade.tachiyomi.ui.reader.viewer.pager.L2RPagerViewer
import eu.kanade.tachiyomi.ui.reader.viewer.pager.R2LPagerViewer
import eu.kanade.tachiyomi.ui.reader.viewer.pager.VerticalPagerViewer
import eu.kanade.tachiyomi.ui.reader.viewer.webgpu.AndroidxWebGpuContinuousViewer
import eu.kanade.tachiyomi.ui.reader.viewer.webgpu.AndroidxWebGpuViewer
import eu.kanade.tachiyomi.ui.reader.viewer.webgpu.WebGpuViewer
import eu.kanade.tachiyomi.ui.reader.viewer.webgpu.WebGpuViewerContinuous
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonViewer
import mihon.app.di.appGraph
import mihon.icons.custommaterialsymbols.CustomMaterialSymbols
import mihon.icons.custommaterialsymbols.rounded.ReaderLeftToRight
import mihon.icons.custommaterialsymbols.rounded.ReaderLongStrip
import mihon.icons.custommaterialsymbols.rounded.ReaderLongStripGaps
import mihon.icons.custommaterialsymbols.rounded.ReaderRightToLeft
import mihon.icons.custommaterialsymbols.rounded.ReaderVertical
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.MobileGear
import tachiyomi.i18n.MR

enum class ReadingMode(
    val stringRes: StringResource,
    val icon: ImageVector,
    val flagValue: Int,
    val direction: Direction? = null,
    val type: ViewerType? = null,
) {
    DEFAULT(MR.strings.label_default, MaterialSymbols.Rounded.MobileGear, 0x00000000),
    LEFT_TO_RIGHT(
        MR.strings.left_to_right_viewer,
        CustomMaterialSymbols.Rounded.ReaderLeftToRight,
        0x00000001,
        Direction.Horizontal,
        ViewerType.Pager,
    ),
    RIGHT_TO_LEFT(
        MR.strings.right_to_left_viewer,
        CustomMaterialSymbols.Rounded.ReaderRightToLeft,
        0x00000002,
        Direction.Horizontal,
        ViewerType.Pager,
    ),
    VERTICAL(
        MR.strings.vertical_viewer,
        CustomMaterialSymbols.Rounded.ReaderVertical,
        0x00000003,
        Direction.Vertical,
        ViewerType.Pager,
    ),
    WEBTOON(
        MR.strings.webtoon_viewer,
        CustomMaterialSymbols.Rounded.ReaderLongStrip,
        0x00000004,
        Direction.Vertical,
        ViewerType.Webtoon,
    ),
    CONTINUOUS_VERTICAL(
        MR.strings.vertical_plus_viewer,
        CustomMaterialSymbols.Rounded.ReaderLongStripGaps,
        0x00000005,
        Direction.Vertical,
        ViewerType.Webtoon,
    ),
    ;

    companion object {
        private const val TAG = "ReadingMode"

        const val MASK = 0x00000007

        fun fromPreference(preference: Int?): ReadingMode = entries.find { it.flagValue == preference } ?: DEFAULT

        fun isPagerType(preference: Int): Boolean {
            val mode = fromPreference(preference)
            return mode.type is ViewerType.Pager
        }

        fun toViewer(
            preference: Int?,
            activity: ReaderActivity,
            sessionBackendOverride: ReadingModePolicy.Backend? = null,
        ): Viewer {
            // P2 preconditions: resolve and validate user data BEFORE any action. A corrupt or
            // unresolved flag never throws; it degrades to the safest option (stock viewer) and
            // is reported to logcat so the value can be diagnosed (fix D1).
            val flag = ReadingModePolicy.resolveFlag(preference)
            if (flag == null) {
                Log.w(TAG, "Corrupt or unresolved reading mode flag $preference; degrading to stock left-to-right pager")
                return L2RPagerViewer(activity)
            }
            val navigation = fromPreference(flag)
            // Backend axis: the single pure decision (T7 truth table) — a session-scoped
            // override (T5 degradation ladder) wins over the preference; a corrupt flag
            // already degraded to the stock path above. Neither writes preferences.
            val mode = ReadingModePolicy.selectBackend(
                preference = activity.appGraph.basePreferences.webGpuViewerMode.get().let {
                    when (it) {
                        eu.kanade.domain.base.BasePreferences.WebGpuViewerMode.STOCK -> ReadingModePolicy.Backend.STOCK
                        eu.kanade.domain.base.BasePreferences.WebGpuViewerMode.HQ -> ReadingModePolicy.Backend.HQ
                        eu.kanade.domain.base.BasePreferences.WebGpuViewerMode.EXPERIMENTAL -> ReadingModePolicy.Backend.EXPERIMENTAL
                    }
                },
                navigationFlag = flag,
                sessionOverride = sessionBackendOverride,
            )
            // DEFAULT is unreachable once the flag is validated; each arm degrades to stock
            // instead of throwing, so no preference value can crash the reader.
            // D4: the horizontal (L2R/R2L) and vertical pager branches are identical per
            // backend — collapsed into shared arms; the backend is the only difference.
            return when (mode) {
                ReadingModePolicy.Backend.EXPERIMENTAL -> when (navigation) {
                    LEFT_TO_RIGHT, RIGHT_TO_LEFT, VERTICAL -> AndroidxWebGpuViewer(activity)
                    WEBTOON -> AndroidxWebGpuContinuousViewer(activity, useGap = false)
                    CONTINUOUS_VERTICAL -> AndroidxWebGpuContinuousViewer(activity, useGap = true)
                    DEFAULT -> L2RPagerViewer(activity)
                }
                ReadingModePolicy.Backend.HQ -> when (navigation) {
                    LEFT_TO_RIGHT -> WebGpuViewer(activity, isReversed = false, isVertical = false)
                    RIGHT_TO_LEFT -> WebGpuViewer(activity, isReversed = true, isVertical = false)
                    VERTICAL -> WebGpuViewer(activity, isReversed = false, isVertical = true)
                    WEBTOON -> WebGpuViewerContinuous(activity, useGap = false)
                    CONTINUOUS_VERTICAL -> WebGpuViewerContinuous(activity, useGap = true)
                    DEFAULT -> L2RPagerViewer(activity)
                }
                ReadingModePolicy.Backend.STOCK -> when (navigation) {
                    LEFT_TO_RIGHT -> L2RPagerViewer(activity)
                    RIGHT_TO_LEFT -> R2LPagerViewer(activity)
                    VERTICAL -> VerticalPagerViewer(activity)
                    WEBTOON -> WebtoonViewer(activity)
                    CONTINUOUS_VERTICAL -> WebtoonViewer(activity, isContinuous = false)
                    DEFAULT -> L2RPagerViewer(activity)
                }
            }
        }
    }

    sealed interface Direction {
        data object Horizontal : Direction
        data object Vertical : Direction
    }

    sealed interface ViewerType {
        data object Pager : ViewerType
        data object Webtoon : ViewerType
    }
}
