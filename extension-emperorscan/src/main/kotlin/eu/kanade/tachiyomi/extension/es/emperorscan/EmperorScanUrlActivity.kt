package eu.kanade.tachiyomi.extension.es.emperorscan

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log

/**
 * Forwards an `https://imperiomanhwa.com/manga/<slug>` deep link into Mihon's global search
 * using the `eu.kanade.tachiyomi.SEARCH` action. The host app picks the search up, scopes it
 * to this extension's package via the `filter` extra, and shows the matching manga.
 *
 * The query is the full URL — [EmperorScan]'s searchMangaRequest detects pasted URLs, fetches
 * the series page directly and surfaces the matching entry (same behaviour the upstream
 * keiyoushi APK exposes through its URL resolution path).
 *
 * Theme.NoDisplay + excludeFromRecents so the handoff doesn't leave an empty task behind —
 * same pattern every other extension's UrlActivity uses.
 */
class EmperorScanUrlActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val data = intent.data
        if (data != null) {
            Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                putExtra("query", data.toString())
                putExtra("filter", packageName)
            }.also { searchIntent ->
                try {
                    startActivity(searchIntent)
                } catch (e: ActivityNotFoundException) {
                    Log.e(packageName, "Unable to launch Mihon search activity", e)
                }
            }
        }
        finish()
    }
}
