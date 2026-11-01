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

    // Sign with the same keystore the app CI uses (release.keystore at the repo root, decoded
    // from the KEYSTORE_BASE64 secret by the workflow). Without it, assembleRelease emits an
    // unsigned APK — fine for CI artifacts, not installable directly.
    val keystoreFile = rootProject.file("release.keystore")
    if (keystoreFile.exists()) {
        signingConfigs {
            create("release") {
                storeFile = keystoreFile
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
                keyAlias = System.getenv("KEY_ALIAS") ?: ""
                keyPassword = System.getenv("KEY_PASSWORD") ?: ""
            }
        }
        buildTypes {
            release {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildTypes {
        release {
            // No minify: the manifest meta-data and the reflected source class name must
            // survive verbatim, and the APK is already tiny (~100KB).
            isMinifyEnabled = false
        }
    }
}

dependencies {
    // Host-provided at runtime (parent classloader). compileOnly keeps them out of the APK.
    compileOnly(projects.sourceApi)
    compileOnly(libs.okhttp)
    compileOnly(libs.jsoup)
}
