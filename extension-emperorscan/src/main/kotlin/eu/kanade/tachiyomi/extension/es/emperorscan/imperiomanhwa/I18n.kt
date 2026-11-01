package eu.kanade.tachiyomi.extension.es.emperorscan.imperiomanhwa

import java.io.InputStreamReader
import java.util.PropertyResourceBundle

/**
 * Tiny i18n helper backed by `assets/i18n/messages_<lang>.properties`.
 *
 * Ships `en` (fallback) and `es`. If the source language is anything else,
 * English is used so the filter/preference UI stays readable.
 *
 * Lookup order:
 *   1. active locale bundle (if it contains the key)
 *   2. English fallback bundle
 *   3. the key itself (never crash on a missing translation)
 */
class I18nBundle(
    private val classLoader: ClassLoader,
    languageTag: String,
    private val supportedTags: Set<String> = setOf("en", "es"),
) {
    private val tag: String = if (languageTag in supportedTags) languageTag else "en"

    private val fallback: PropertyResourceBundle by lazy { load("en") }
    private val active: PropertyResourceBundle by lazy {
        if (tag == "en") fallback else load(tag)
    }

    private fun load(tag: String): PropertyResourceBundle {
        val stream = classLoader.getResourceAsStream("assets/i18n/messages_$tag.properties")
            ?: error("Missing i18n bundle: assets/i18n/messages_$tag.properties")
        return PropertyResourceBundle(InputStreamReader(stream, Charsets.UTF_8))
    }

    fun get(key: String): String =
        active.takeIf { it.containsKey(key) }?.getString(key)
            ?: fallback.takeIf { it.containsKey(key) }?.getString(key)
            ?: key
}
