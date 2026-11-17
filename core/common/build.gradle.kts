plugins {
    alias(mihonx.plugins.android.library)
    alias(mihonx.plugins.spotless)

    alias(libs.plugins.metro)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "eu.kanade.tachiyomi.core.common"
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
        )
    }
}

dependencies {
    implementation(projects.core.metro)
    implementation(projects.i18n)

    api(libs.logcat)

    api(libs.rxJava)

    api(libs.okhttp.core)
    api(libs.okhttp.logging)
    api(libs.okhttp.brotli)
    api(libs.okhttp.dnsOverHttps)
    api(libs.okio)

    api(libs.apollo)

    implementation(libs.image.decoder)

    implementation(libs.unifile)
    implementation(libs.archive)

    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.serialization.jsonOkio)

    api(libs.androidx.preference)
    implementation(libs.androidx.webkit)

    implementation(libs.jsoup)

    // Sort
    implementation(libs.natural.comparator)

    // JavaScript engine
    implementation(libs.quickJs)

    testImplementation(libs.bundles.test)
    testImplementation(libs.kotlinx.coroutines.test)
    // Real org.json for JVM unit tests: the Android SDK ships only a stub whose methods
    // throw RuntimeException("Stub!"), so FetchSessionProtocolTest (which exercises
    // JSONObject/optString/optInt/optJSONObject via the WebView fallback protocol) needs
    // a concrete implementation on the test classpath.
    testImplementation("org.json:json:20240303")
    testRuntimeOnly(libs.junit.platform.launcher)

    implementation(libs.metro.runtime)
}
