# androidx.webgpu migration — session progress

Branch: `feature/webgpu-clean` | Session 2026-11-01 | Approved scope: #5 #6 #7 — ALL DELIVERED

## Hotfix (2026-11-01, hours after SESSION-COMPLETE): reader crashed when the toggle was on
- Crash log (POCO peridot, Android 16, build e561462): `UnsatisfiedLinkError` at
  `GPU.createInstance` from `AndroidxWebGpuProbe`. 100% reproducible with the toggle enabled:
  the probe called the JNI method without ever loading `libwebgpu_c_bundled.so` (the renderer's
  `createWebGpu()` path loads it via the helper, but the probe bypassed the helper), and its
  `catch (e: Exception)` cannot catch a `LinkageError`. The deobfuscated stack pointed at
  `MoreViewModel$1` because R8 merged the probe's `LaunchedEffect` lambda with an identical one.
- Fix: the probe calls androidx.webgpu.helper's top-level `initLibrary()` before
  `createInstance` and catches `Throwable` (rethrowing `CancellationException`).
- Regression test: `AndroidxWebGpuProbeTest` — on the JVM the 408 classes exist
  (testImplementation(libs.webgpu)) but the .so does not, so the test reproduces the exact
  production failure; verified it fails on the old probe and passes on the fixed one.
- Checked: `libwebgpu_c_bundled.so` (arm64) is 16 KB-page aligned — Android 16 devices work
  once the library actually loads; the crash was purely "library never loaded".

## Final state
- `7ae2f8f` #7 settings + accessibility + tests (CI validating at time of writing)
- `4fc1233` #6 zero-copy upload + FIX: placed-pages draw was silently missing from cac5014 — CI ✅
- `cac5014` #5b continuous/webtoon mode (base class + AndroidxWebGpuContinuousViewer + routing) — CI ✅
- `a8b5feb` #5a gestures: pinch zoom/pan/double-tap/long-press — CI ✅
- `8d424a4` #3 windowed page cache + LRU eviction — CI ✅
- `ae27612` #4 page progress/error/retry overlays — CI ✅
- `8d28faf` #2 auto-fallback to HQ/stock viewer — CI ✅
- `bdc6e37`+`4a0d2fb` #1 real surface rendering — CI ✅

## What #7 added
- Settings > Advanced > Reader: androidx.webgpu toggle (pref_androidx_webgpu_viewer + summary).
- TalkBack: surface announces "Page N" / "Page N, loading" / "Page N, failed to load" (both viewers).
- AndroidxWebGpuPageCachePolicy: pure window/victim functions used by the base viewer + 9 unit
  tests (app/src/test/.../webgpu/AndroidxWebGpuPageCachePolicyTest.kt).

## Known limitations / next steps (for a future session)
- Continuous viewer: no pinch-zoom yet (HQ keeps it); no transition pages between chapters;
  layout jumps when page aspects resolve; preload 2/5, cache 10.
- Paged viewer: no navigation-zone customization (fixed halves), no fling page turns.
- Zero-copy: real AHardwareBuffer import exists (GPUAndroidHardwareBufferUtil) but the ca.mpreg
  decoder only outputs CPU ByteBuffers — revisit if the decoder ever gains AHB output.
- Reader resume position still lands on the first page of the chapter (same as phase 4 behavior).

## Key facts (kept for reference)
- Build: `export JAVA_HOME=/home/z/jdks/jdk-21.0.13+9 && ./gradlew <task> -Pdist=ci -Pinclude-telemetry=false --console=plain`
- Local: spotlessCheck / compileReleaseKotlin / testDebugUnitTest only. assembleRelease → CI.
- moko stringResource: `tachiyomi.presentation.core.i18n.stringResource` (NOT androidx.compose.ui.res).
- MultiEdit lesson: ALWAYS grep-verify edited regions after every batch; one huge edit silently
  failed once and compiled anyway (unused field).

SESSION-COMPLETE
