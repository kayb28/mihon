# androidx.webgpu migration — autonomous session progress

Branch: `feature/webgpu-clean` | Started: 2026-11-01 ~05:00 UTC | User: asleep, approved #5 #6 #7
Watchdog cron jobId: 492060 (deletes itself when this file contains SESSION-COMPLETE)

## Done (CI green)
- `cac5014` #5b continuous/webtoon mode: AndroidxWebGpuBaseViewer (extracted lifecycle) + AndroidxWebGpuContinuousViewer (scroll strip, per-frame placed-pages draw, overlays per page) + routing for all modes + fallback guard on base. CI pending on push.
- `a8b5feb` #5a gestures: pinch zoom/pan/double-tap/long-press on paged viewer; renderer setTransform (NDC clamp per frame)
- `8d424a4` #3 windowed page cache + LRU eviction (PRELOAD_BEHIND=2, PRELOAD_AHEAD=3, PAGE_CACHE_SIZE=7)
- `ae27612` #4 page progress/error/retry overlays
- `8d28faf` #2 auto-fallback to HQ/stock viewer
- `bdc6e37`+`4a0d2fb` #1 real surface rendering

## Todo this session
- [x] #5 gestures → a8b5feb
- [x] #5 continuous/webtoon → cac5014 (CI validating)
- [ ] #6 zero-copy upload (IN PROGRESS: checking androidx.webgpu for external-memory APIs; else reusable staging buffer in uploadPixels — runs only on WebGPU dispatcher thread so a grow-only buffer field is thread-safe)
- [ ] #7 settings wiring + accessibility + unit tests → commit

## Key facts
- Build: `export JAVA_HOME=/home/z/jdks/jdk-21.0.13+9 && ./gradlew <task> -Pdist=ci -Pinclude-telemetry=false --console=plain`
- Local allowed: `:app:spotlessCheck`, `:app:compileReleaseKotlin`, `:app:testDebugUnitTest`. NEVER assembleRelease locally — CI does it.
- ktlint 1.8.0 pinned. CI order: spotless → unit tests → assembleRelease (2 jobs: release + unminified).
- CI check: `TOKEN=$(git remote get-url origin | sed -n 's|https://\([^@]*\)@github.com.*|\1|p')` (never print it)
  then `curl -s -H "Authorization: token $TOKEN" https://api.github.com/repos/kayb28/mihon/actions/runs?branch=feature/webgpu-clean&per_page=1`
- HQ viewer reference: `WebGpuViewer.kt` (ca.mpreg) — preload 3/2, cacheSize 7, evictFarthestPage.
- `Page.statusFlow`/`progressFlow` are StateFlow (replay on collect) — watchers safe to cancel/recreate.
- Renderer (`AndroidxWebGpuSurfaceRenderer`): Dawn calls only inside `environment.execute` (single dispatcher thread).
  `stop()` is synchronous; textures die with environment.
- Viewer monitor: `synchronized(gpuImages)` guards gpuImages/watchJobs/uploadJobs/readingOrder. destroy() outside monitor.

## #5 scope notes
- Gestures: Compose detectTransformGestures on the Box; transform applied at writeParams time
  (NDC scale *= zoom, offset += pan) — no WGSL change needed. Clamp pan to page bounds.
- Continuous: check `ReadingMode.toViewer` routing first. If flag-on webtoon currently lands on the
  paged androidx viewer, that's broken → build AndroidxWebGpuContinuousViewer: single surface,
  renderer draws visible pages each with own NDC rect (extend setPage→setPages), vertical scroll
  with fling, current = viewport center, same windowed cache (bigger window), same presentation
  overlays. If too risky, keep webtoon on HQ viewer and document.

## #6 scope notes
- uploadPixels re-packs rows into a per-call allocateDirect staging buffer when rowBytes % 256 != 0.
  Runs only on the WebGPU dispatcher thread → reusable grow-only staging ByteBuffer field is safe.
- Search androidx.webgpu classes for external memory / AHardwareBuffer import APIs before choosing.

## #7 scope notes
- Settings: wire BasePreferences.androidxWebGpuViewer into reader settings screen (entry + state).
- A11y: contentDescription on SurfaceView (page number), semantics on overlays.
- Tests: extract pure window/eviction logic into testable object + JUnit tests (no Robolectric needed).
