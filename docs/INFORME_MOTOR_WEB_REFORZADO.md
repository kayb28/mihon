# INFORME_MOTOR_WEB_REFORZADO.md — motor web secundario: gatillo «todos los fallos» (PROMPT 7)

> Rama `feat/p7-webview-fallback-all-failures` (base: `main` `7d1092685`). Ciclo red-green por
> comportamiento W1–W5. **PR abierto, SIN MERGE**: la puerta de merge es la validación en
> DISPOSITIVO del usuario sobre fuentes con fallos reales (checklist abajo) + su OK explícito.

## Decisión de diseño registrada

Decisión del usuario (PROMPT 7): el disparador del fallback WebView pasa de una lista blanca
(~403/503 + GET/POST) a **«todos los fallos»** = cualquier código HTTP de error (4xx/5xx) **y**
la rama de excepción de red (IOException etc.). Racional: la mayoría de los fallos son fallos de
la primera petición; el modelo es de tres capas — 1) primer intento OkHttp → 2) segundo intento
automático WebView → 3) triaje manual del usuario (informes de fuentes problemáticas). «Mejor»
significa más CAPAZ (JS, UA real, cookies, anti-bot), no más rápido; el coste (lento, batería)
lo acotan las guardas. La decisión, junto con la exención técnica W3 (vetable por el usuario
hasta la puerta de merge), queda registrada en `docs/REGISTRO.md` (entrada 2026-11-27).

Principio «motor pequeño propio» intacto: el motor grande (OkHttp/NetworkHelper) solo se
ENVUELVE — la extracción `WebViewFallbackPolicy` es verbatim de las reglas auditadas y toda la
nueva lógica vive en la capa pura de `core:common`; el interceptor solo cablea.

## Mapa conducta → test → commit

| Conducta | Test (WebViewFallbackPolicyTest) | Commit | Estado CI |
|---|---|---|---|
| Rojo: extracción verbatim + tests W1–W5 que fallan por comportamiento | 9 FAILED / 9 | `682889285` | run [45918320778](https://github.com/kayb28/mihon/actions/runs/45918320778) — compilación OK, Spotless OK, 9 FAILED = rojo legítimo |
| **W1** cualquier 4xx/5xx dispara (antes solo ~403/503; los que ya disparaban siguen igual) | `W1 any 4xx code triggers`, `W1 any 5xx code triggers`, `W1 success and redirect codes do not trigger`, `W1 codes that already triggered keep triggering` | `408e7dc9a` | verde final en `dcba12e2b` |
| **W2** IOException/SocketTimeout también disparan; si el WebView falla, el error ORIGINAL se propaga (nunca se tragan errores) | `W2 a network exception triggers`, `W2 a socket timeout triggers`, `W2 non-network exceptions do not trigger` | `689a5375c` (+ estilo `716030095`, compilación `dcba12e2b`) | verde final en `dcba12e2b` |
| **W3** sin conectividad total (avión/sin red) NO se dispara (exención técnica, vetable) | `W3 an HTTP error without connectivity does not trigger`, `W3 a network exception without connectivity does not trigger`, `W3 with connectivity the attempt is still allowed` | `9c2713a55` | verde final en `dcba12e2b` |
| **W4** guardas: 1 reintento/petición, cerradura por host, pool 2 min intactos; cooldown por host CONFIGURABLE (default 10 min); no-amplificación (N fallos → ≤1 WebView) | `W4 cooldown is active by default and conservative`, `W4 N failing requests to a dead host do not parade WebViews`, `W4 the cooldown is configurable`, `W4 a successful attempt does not start the cooldown`, `W4 unsupported methods never trigger`, `W4 distinct hosts do not share the cooldown` | `792149002` | verde final en `dcba12e2b` |
| **W5** registro por host «fallback usado / resultado» alimentando los informes de fuentes problemáticas | `W5 the aggregator summarizes attempts per host`, `W5 a host without attempts does not appear in the report` | `b0ca58414` | verde final en `dcba12e2b` |
| Docs: ESTADO.md (diagnóstico PROMPT-D) | — | `a2765b7b2` | docs (CI roja esperada por los tests) |

**Run verde final**: [45929735205](https://github.com/kayb28/mihon/actions/runs/45929735205)
(commit `dcba12e2b`) — Spotless & Unit Tests **success** con los 18 tests de
`WebViewFallbackPolicyTest` PASSED (W1–W5 completos), Build Release APK **success** (firma
ESTABLE), Build Unminified **success**.

Serie completa de runs de la rama: roja `45918320778` (`682889285`) → intermedias
`45928512213`…`45928732458` (convergencia: style/compile) → **verde `45929735205`**.

## Qué cambió en el código (resumen)

- `WebViewFallbackPolicy.kt` (nueva, capa pura JVM): decisión de disparo (W1: 400..599; W2:
  `cause is IOException`; W3: `!hasConnectivity`), cooldown por host con reloj inyectable
  (W4), y `FallbackUsageTracker` + `usageReport()` (W5). Extraída verbatim del interceptor en
  el commit rojo; los behaviors aterrizaron uno por commit.
- `WebViewInterceptor.kt`: dos hooks protegidos nuevos (`shouldInterceptException`,
  `interceptException`) — el `chain.proceed` envuelto ofrece el segundo intento WebView ante
  IOException y, si no produce respuesta, **relanza la excepción original** (W2). Herencia
  existente (CloudflareInterceptor) intacta.
- `WebViewFetchFallbackInterceptor.kt`: cablea el policy (incluida la sonda
  ConnectivityManager de W3), factoriza el intento único en `attemptFallback` (guardas W4
  intactas: 1 reintento por petición, cerradura por host, pool 2 min), registra resultado y
  emite la línea de log por host (W5). Cooldown configurable vía preferencia
  `webview_fallback_cooldown_minutes` (default 10 min, `NetworkPreferences`).
- `NetworkPreferences.kt`: preferencia nueva (W4). Nada más. Cero cambios en extensiones,
  motor grande, Bloque B, workflows, verify_apk_signature.py, PROBLEMAS.md.

## Checklist de validación EN DISPOSITIVO (puerta de merge — la hace el usuario)

Observable en pantalla con fuentes reales que fallen:

- [ ] **W1**: una fuente que devuelva 404/429/520 en el primer intento muestra el toast
      «fallback WebView activo» (o la carga termina bien) — antes esos códigos ni probaban.
- [ ] **W2**: con una fuente caída (timeout de conexión), la app tarda el extra del intento
      WebView y LUEGO muestra el error original de red (mismo mensaje que antes, nunca uno nuevo).
- [ ] **W4**: tras un fallo del fallback en un host, peticiones siguientes a ese host NO
      reintentan WebView durante el cooldown (sin desfile de WebViews / sin picos de batería);
      configurable en Ajustes → Avanzado (minutos de enfriamiento).
- [ ] Sin regresión: fuentes sanas cargan igual (el fallback solo actúa tras fallo).

Sin el OK explícito del usuario sobre esta checklist: **NO merge**.

## En criollo

Antes, si una fuente te fallaba con 403 o 503, la app la reintentaba con el WebView (el
navegador de verdad, que pasa los anti-bot). Si fallaba con 404, 429, 520… o si directamente
se caía la conexión (timeout), NI lo intentaba: te comías el error al toque. Ahora es al revés:
**cualquier fallo de la primera pasada recibe su segunda pasada automática en WebView**. Si el
WebView tampoco puede, te llega el error de siempre, tal cual — nada se esconde. Dos salvedades:
si estás en modo avión no lo intenta (no puede funcionar, y así no gasta batería); y si un sitio
ya demostró que el WebView no lo salva, ese sitio entra en cuarentena 10 minutos (ajustable)
para no abrir WebViews en cadena. Todo lo demás — el reintento único por petición, la fila por
sitio, la piscina de WebViews calientes — queda como estaba.

## 📲 BUILD PARA INSTALAR

- Artefacto: `INSTALAR-dcba12e2b67205f392f070e270cf61dd564d20f1` (run
  [45929735205](https://github.com/kayb28/mihon/actions/runs/45929735205), job «Build Release
  APK», retención 30 días).
- APK (nombre idéntico al del workflow):
  `MihonFix-0.21.3-build-dcba12e-20261127-0245-arm64.apk`
- sha256: `f823f715e134e678f066605c06cc8ad977dc03879660b3e5afe6810da0ee9b3b`
- Tamaño: 35 225 030 bytes (≈33,6 MiB)
- Verificación de firma (`python3 scripts/verify_apk_signature.py <ruta al apk>`):
  cert SHA-256 `DE7889F34D731EF961794F0837B78B86D025387F20E036F357C94742D1296AD0` —
  **ESTABLE (instala encima de la anterior sin desinstalar)**.
