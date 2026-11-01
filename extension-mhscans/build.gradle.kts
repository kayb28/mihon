/*
 * MHScans extension APK — built inside the fork so it ships from the same CI that builds the
 * app, signed with the same keystore.
 *
 * It is a plain `com.android.application` module (extension APKs are applications, not
 * libraries) whose manifest carries the tachiyomi.* / tachiyomix.* meta-data the host's
 * ExtensionLoader reads at install time.
 *
 * source-api, okhttp and jsoup are compileOnly: the host app provides them at runtime through
 * the PathClassLoader parent chain, so they must NOT be packaged in the extension APK — that
 * would clash with the host's copies (and bloat a 100KB APK to megabytes).
 */
plugins {
    alias(mihonx.plugins.android.application)
}

android {
    namespace = "eu.kanade.tachiyomi.extension.es.mhscans"

    defaultConfig {
        applicationId = "eu.kanade.tachiyomi.extension.es.mhscans"

        // Follows the upstream convention MMmmNN: 1.6.71 was the previously shipped APK
        // (versionCode 106071). Bump the patch on every rebuild so Mihon offers the update.
        versionCode = 106072
        versionName = "1.6.72"
    }

    buildTypes {
        val debug = getByName("debug")

        release {
            // No minify: the manifest meta-data and the reflected source class name must
            // survive verbatim, and the APK is already tiny (~100KB).
            isMinifyEnabled = false

            // Same signing scheme as the app (app/build.gradle.kts signs release with the
            // debug config — either the runner's default debug keystore or the repo-local
            // keystore.properties). A debug-signed extension APK installs fine; what matters
            // for Mihon is the extension's own meta-data, not signature parity with the host.
            signingConfig = debug.signingConfig
        }
    }
}

dependencies {
    // Host-provided at runtime (parent classloader). compileOnly keeps them out of the APK.
    // okhttp: only the core client/Request/Response types are referenced — the host's client
    // already carries the brotli/zstd interceptors, so the core artifact is all we compile against.
    compileOnly(projects.sourceApi)
    compileOnly(libs.okhttp.core)
    compileOnly(libs.jsoup)
}
