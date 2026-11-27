# INFORME T-F — Fallo F: imágenes largas COMPRIMIDAS en EXPERIMENTAL (calidad)

Rama: `fix/fallo-f-calidad` (cortada de `main` @ `c196c91`). Encargo: PROMPT 27,
tarea T-F. Fecha: 2026-11-27. Agente: GLM.

## Problema reportado

Tras el merge de T-E, el visor EXPERIMENTAL muestra las imágenes largas
«comprimidas/achicadas a ilegible»; el lector HQ no tiene ese problema (pista del
usuario: la solución está en cómo lo hace HQ).

## Diagnóstico (raíz real)

- El muestreo de T-E ES nativo y correcto: `createFromImage` planifica teselas
  (`GpuTilePlanPolicy.planTiles`) y `uploadTilePixels` copia bandas exactas sin escalar
  — la línea de teselas NO es la causa.
- La causa está en la **colocación** del camino paginado:
  `AndroidxWebGpuSurfaceRenderer.pageToTileQuads` computaba el fit como
  `min(surfaceWidth/page.width, surfaceHeight/page.height)` — o sea, meter la página
  ENTERA dentro del viewport. Para 800×50000 en una pantalla ~1080×2400 eso es una
  escala global ≈ 0.048: la tira entera exprimida en la altura de la pantalla.
  Antes de T-E esa página crasheaba en `createTexture` (Fallo E), así que la colocación
  comprimida nunca llegó a verse; T-E la hizo visible. Es la regresión de legibilidad
  que reporta el usuario: E1 «no escalar» se cumplía en el muestreo pero NO en toda la
  ruta de renderizado.
- HQ no comprime porque su camino (subsampling / decodificación por regiones) VENTANEA
  el contenido: ajusta el ANCHO y deja recorrer el resto — el mismo windowing que T6a
  cableó como «loadPage por página de la ventana».

## Solución

Policy pura nueva `LongStripFitPolicy` (mismo patrón que `GpuTilePlanPolicy` /
`ContinuousPageReportPolicy`: decisión pura JVM-testable, wiring delimitado):

- Página que cabe en el viewport: la matemática de fit anterior BIT A BIT (E3 intacto —
  páginas normales sin cambio alguno).
- Página más alta que el viewport: colocación a **fit-ancho** (nunca escala global <1
  respecto del fit-ancho), ventana fijada al INICIO de la página, pan vertical acotado
  con rango ASIMÉTRICO (de la ventana en el tope a la ventana en el pie) para que el
  viewport nunca salga de la página (sin huecos).
- Wiring del pan (F2): el paginado solo acumulaba pan con zoom > 1; ahora, si la página
  actual es más alta que el viewport a fit-ancho (`tallPagePanRange`), el arrastre
  vertical desliza la ventana con zoom 1 y el pase de página ocurre SOLO en el borde de
  la ventana — el windowing de HQ. Páginas normales: T4 intacto.
- El plan de teselas y el muestreo nativo de T-E quedan intactos; todo tile sigue ≤
  `maxTextureDimension2D` (F3 con test propio).

## Comportamiento → test → commit

| ID | Comportamiento | Test (JVM, `LongStripFitPolicyTest`) | Commits |
| --- | --- | --- | --- |
| F1 | Sin escala global <1: página alta a fit-ancho con ventana arriba + pan acotado; página normal = fit previo bit a bit (E3) | `F1 a tall page…`, `F1 the initial view…`, `F1 panning…`, `E3 a normal page…` | rojo `010d28c` → verde `1162a24` |
| F2 | Legibilidad equivalente a HQ: ancho lleno a escala nativa; misma fracción (1.0) que el continuo EXPERIMENTAL por defecto; la ventana se recorre con el dedo a fit y el pase de página solo en el borde | `F2 a tall page reads like HQ…`, `F2 the paged tall-page window matches…` (JVM) + wiring en `AndroidxWebGpuViewer` → checklist de dispositivo | verde `62e8711` + wiring `d027834` |
| F3 | Sin regresión del Fallo E: el plan de teselas no cambia y los quads colocados particionan la página exactamente (tope de la primera tesela = tope de la página; pie de la última = pie de la página) | `F3 the tile plan still bounds…` | por construcción (`createFromImage` y `GpuTilePlanPolicy` intactos) + test propio en la serie; este informe cierra la fila |

Commits de formato/expectativa exigidos por CI (no cuentan como rojo/verde):
`ea3cc04` (indentación ktlint), `10458ff` (':' ilegal en el nombre de un test),
`7c3119e`/`833e642` (signo y recorrido del pan en la expectativa F1 — la policy quedó
con rango asimétrico y el test lo sigue).

## Rojo / verde (CI)

- Rojo: `010d28c` falló POR FORMATO (run 46029848758) — no cuenta; `ea3cc04` falló por
  nombre de test ilegal — no cuenta. Serie roja re-creada limpia en la rama de
  evidencia `evidence/f-red` (`7d15af7`, run 46033850235): `compileDebugUnitTestKotlin`
  FAILED — símbolos `LongStripFitPolicy` inexistentes, rojo POR COMPORTAMIENTO.
- Verde F1+F2+F3: la punta de `fix/fallo-f-calidad` `833e642` — run 46036928747
  **success** (Spotless & Unit Tests verde con los 9 tests de la policy; los runs
  intermedios 46033944514 y 46035775303 fallaron por la expectativa del pan, corregida
  en `7c3119e`/`833e642` junto con el clamp asimétrico de la policy).

## STOP — diagnóstico, no activado

La condición STOP del encargo era «si la raíz no está en la ruta de renderizado de T-E
(decoder, filtrado de texturas)». El decoder (`ca.mpreg:imagedecoder`, dependencia
cerrada) y el sampler no fueron necesarios tocar: la raíz SÍ está en la ruta de
renderizado (la colocación del camino paginado), se corrigió ahí y el muestreo nativo
de T-E quedó sin cambios.

## Checklist de dispositivo (usuario)

1. La misma tira webtoon ultra-alta que reportaste: modo paginado EXPERIMENTAL → se ve
   al ANCHO de la pantalla, nítida, abriendo en la parte de arriba de la página.
2. Arrastrar verticalmente: la ventana recorre la tira hasta el final (pan con zoom 1),
   sin huecos arriba/abajo; la página siguiente abre también en su tope.
3. En el borde inferior, seguir deslizando hacia arriba → pasa a la página siguiente;
   en el borde superior, deslizar hacia abajo → página anterior. Las páginas normales
   pasan página con el swipe de siempre (sin pan fantasma).
4. Zoom (pellizco / doble toque): aumenta desde el fit-ancho, sin achicarse debajo.
5. La misma tira en HQ: legibilidad equivalente (F2) — comparar la misma página en los
   dos visores.
6. Modo continuo EXPERIMENTAL (webtoon): sin cambios (la policy solo toca el paginado).
7. Sin crash con contenido que supera el límite GPU (Fallo E sigue resuelto — F3).

## PROBLEMAS.md

- Fila Fallo E: nota «fix merged but quality regression reported → Fallo F».
- Nueva fila Fallo F (abierta hasta la validación del usuario en dispositivo).

## En criollo

Antes la página larguísima reventaba la GPU; con el arreglo de las teselas ya no
revienta, pero el lector la «achataba» entera en la pantalla y quedaba ilegible. Ahora
el lector EXPERIMENTAL hace lo mismo que HQ: te muestra la tira al ancho de la pantalla
— nítida, píxel por píxel — y la vas recorriendo con el dedo; al llegar al final,
el siguiente swipe pasa de página. Las páginas normales ni se enteran: se ven exactamente
igual que siempre.

## 📲 BUILD PARA INSTALAR

- Branch: `fix/fallo-f-calidad`
- Commit: `833e642` (punta al cerrar el código; el commit de docs no cambia la app)
- Run CI: 46036928747 (job «Build Release APK», artefacto `INSTALAR-833e642…`)
- APK: `MihonFix-0.21.3-build-833e642-<yyyymmdd-hhmm UTC>-arm64.apk`
- Firma: verificar con `python3 scripts/verify_apk_signature.py <apk>` → ESTABLE
  (`DE7889F3…D1296AD0`) — el agente descarga y verifica el artefacto; resultado
  definitivo (sha256 + tamaño) anotado en `docs/REGISTRO.md`.
- Si la run no produce artefacto: «Ninguna — encargo sin build nueva» + motivo en
  REGISTRO.
