# Informe de verificación T-10.0 — auditoría P0/P1 (PROMPT 10)

Fecha: 2026-11-28 (UTC). Rama base: `main` = `c196c91`.

**Nota sobre la base:** la auditoría cita punta `bd4aa16e`; esa punta NO existe en el
repo (`main` real = `c196c91`, verificado con `ls-remote`). Los veredictos siguientes se
emiten contra el árbol real; todo hallazgo se leyó en el fichero completo, no en extractos.

Regla de oro aplicada: hallazgos que no se sostienen se registran `NO-CONFIRMADO` con
evidencia; nada se parchea por aplaudir al auditor.

| Ref | Hallazgo | Veredicto | Evidencia |
|---|---|---|---|
| V1 → P1-1 | `hasConnectivity()` evaluado en cada respuesta (IPC por respuesta, incluidos 200) | **CONFIRMADO** | `WebViewInterceptor.kt:73-90` — `intercept()` llama a `shouldIntercept(response)` para TODA respuesta; `WebViewFetchFallbackInterceptor.kt:120-129` — `hasConnectivity()` es argumento eager de `policy.shouldAttempt(...)`, se ejecuta aunque el método/código ya descarten. P1-1 se queda en P1 → T-10.6 |
| V2 → P1-11 | `loadPage()` child del job → `cancel()` aborta la descarga | **NO-CONFIRMADO** | `HttpPageLoader.kt:33` — `scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)` propio del loader; las descargas corren en `scope.launch` (:142 `launchLoadOnce`, :183 `chunkProcessJob`), NO como children del caller. `loadPage` (suspend, :62) solo espera; cancelar al caller cancela la espera, no la descarga (y :254-257 re-encola la cancelada). Sin parche |
| V3 → P1-10 | `destroy()` de texturas no thread-safe | **PARCIAL** — sin parche | La evicción corre bajo `lock` desde DOS hilos: render (getPage :493-504) y preload IO (`scope.launch(Dispatchers.IO)` :537-542 → `preloadPages` → `getPage`). Existe protección de pin (`cleanupImage`/`deferredCleanup`, :356-374) pero solo cubre la imagen dibujada por `pinnedFromPage`. `Image.cleanup()` vive en el AAR binario `ca.mpreg:webgpuviewer` (`libs.versions.toml:202`) — la thread-safety no se puede demostrar ni refutar desde el árbol. Canalizar al hilo de render es rediseño (prohibido por el encargo): pasa a **validación en dispositivo**, registrado |
| V4 → P1-7 | Capa del `DomainOverrideInterceptor` | **CONFIRMADO: app-level** | `NetworkHelper.kt:50` — `.addInterceptor(DomainOverrideInterceptor(...))` (los network interceptors son solo el logging :60). Los app interceptors corren UNA vez por call; los redirects no re-entran. T-10.12 = solo línea de KDoc |
| V5 → P0-2 | Guards antes de `MigrateMangaUseCase` | **PARCIAL** — P0-2 CONFIRMADO | La vía del reparador YA verifica capítulos antes de migrar (`RepairLinksViewModel.kt:414-425`, `chapterCount <= 0` → NEEDS_REVIEW). Pero `MigrationListViewModel.kt:517,579` y `MigrateMangaDialog.kt:219` llaman al use case directo, sin guard de capítulos. El guard debe vivir EN el use case (capa correcta: cubre a todos los callers) → T-10.1 |

## Consecuencias sobre el plan

- **T-10.6** se ejecuta tal cual (P1-1 CONFIRMADO).
- **T-10.12** = solo KDoc (`una reescritura por call; los redirects no re-entran`), sin guarda de profundidad.
- **T-10.15 NO se implementa**: PARCIAL (no demostrable con AAR binario), riesgo de
  rediseño > beneficio; queda como caso de validación de dispositivo (crash/ruido
  visual al hacer saltos largos con muchas texturas en caché).
- **T-10.7/8/9, T-10.10, T-10.11, T-10.13, T-10.14** y los P0 se ejecutan tal cual.

## Hallazgos sin test JVM (inspección)

T-10.3 (runCatching de portada/borrado), T-10.7 (client obsoleto), T-10.8
(postDelayed), T-10.9 (dispatchMessage), T-10.12 (KDoc), T-10.15 (PARCIAL):
verificación por inspección + checklist de dispositivo, según §5 del encargo.
