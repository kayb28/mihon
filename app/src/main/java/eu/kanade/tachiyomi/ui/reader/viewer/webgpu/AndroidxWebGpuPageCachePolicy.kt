package eu.kanade.tachiyomi.ui.reader.viewer.webgpu

import kotlin.math.abs

/**
 * The androidx.webgpu viewers' page-cache policy (WebGPU migration, phase 8), as pure
 * functions: the preload window around the current page, and which cached page to evict once
 * the cache is over budget. Extracted from [AndroidxWebGpuBaseViewer] so the policy is
 * unit-testable without a reader activity or a GPU environment.
 */
internal object AndroidxWebGpuPageCachePolicy {

    /**
     * Index bounds (inclusive) of the preload window around [anchor] in a list of [size] pages:
     * [behind] pages before it and [ahead] after it in reading order, clipped to the list. An
     * anchor outside the list clips to its nearest end; an empty list has no window.
     */
    fun window(anchor: Int, size: Int, behind: Int, ahead: Int): IntRange {
        if (size <= 0) return IntRange.EMPTY
        val clipped = anchor.coerceIn(0, size - 1)
        return (clipped - behind).coerceAtLeast(0)..(clipped + ahead).coerceAtMost(size - 1)
    }

    /**
     * Which of the [candidates] to evict: the one farthest from the anchor's
     * [anchorPosition] in reading order — never the [anchor] itself. A candidate with no
     * position in [positions] (a page of chapters the reader dropped) is farther than
     * everything, so it goes first; ties keep the caller's iteration order. Null when nothing
     * but the anchor is evictable.
     */
    fun <T> victim(candidates: Set<T>, positions: Map<T, Int>, anchor: T?, anchorPosition: Int): T? {
        return candidates
            .filterNot { it === anchor }
            .maxByOrNull { candidate ->
                positions[candidate]?.let { position -> abs(position - anchorPosition) } ?: Int.MAX_VALUE
            }
    }
}
