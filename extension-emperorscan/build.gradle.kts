/*
 * Emperor Scan extension APK — same in-fork build model as :extension-mhscans:
 * a plain `com.android.application` module whose manifest carries the
 * tachiyomi.* / tachiyomix.* meta-data the host's ExtensionLoader reads at
 * install time, signed with the same (debug) scheme as the app so CI emits an
 * installable APK without keystore secrets.
 *
 * source-api, okhttp, jsoup, kotlinx-serialization, webkit and injekt are
 * compileOnly: the host app provides them at runtime through the
 * PathClassLoader parent chain (:app → :data → :core:common), so they must
 * NOT be packaged in the extension APK — duplicates would clash with the
 * host's copies (and bloat a ~150KB APK to megabytes).
 */
plugins {
    alias(mihonx.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "eu.kanade.tachiyomi.extension.es.emperorscan"

    defaultConfig {
        applicationId = "eu.kanade.tachiyomi.extension.es.emperorscan"

        // MMmmNN convention: the previously shipped upstream APK was v1.6.74
        // (versionCode 106074). Bump the patch on every rebuild so Mihon
        // offers the update.
        versionCode = 106075
        versionName = "1.6.75"
    }

    buildTypes {
        val debug = getByName("debug")

        release {
            // No minify: the manifest meta-data and the reflected source class
            // name must survive verbatim; the APK is already tiny.
            isMinifyEnabled = false

            // Same signing scheme as the app (app/build.gradle.kts signs
            // release with the debug config).
            signingConfig = debug.signingConfig
        }
    }
}

dependencies {
    // Host-provided at runtime (parent classloader). compileOnly keeps them
    // out of the APK.
    compileOnly(projects.sourceApi)

    // The multisrc parses the Astro `<script data-page=app>` Inertia payloads
    // with kotlinx.serialization — the host bundles it via :data.
    compileOnly(libs.kotlinx.serialization.json)

    // WebViewReader uses WebViewCompat.addWebMessageListener — the host
    // bundles androidx.webkit via :core:common.
    compileOnly(libs.androidx.webkit)

    // Context/SharedPreferences lookup (same as source-api's sourcePreferences()).
    compileOnly(libs.injekt)

    // Dispatchers for the headless WebView execution.
    compileOnly(libs.kotlinx.coroutines.core)

    compileOnly(libs.okhttp.core)
    compileOnly(libs.jsoup)
}
