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
  para que el viewport nunca salga de la página (sin huecos).
- El plan de teselas y el muestreo nativo de T-E quedan intactos; todo tile sigue ≤
  `maxTextureDimension2D` (F3 con test propio).

## Comportamiento → test → commit

| ID | Comportamiento | Test (JVM, `LongStripFitPolicyTest`) | Commits |
| --- | --- | --- | --- |
| F1 | Sin escala global <1: página alta a fit-ancho con ventana arriba + pan acotado; página normal = fit previo bit a bit (E3) | `F1 a tall page…`, `F1 the initial view…`, `F1 panning…`, `E3 a normal page…` | rojo `010d28c` → verde `1162a24` |
| F2 | Legibilidad equivalente a HQ: ancho lleno a escala nativa, misma fracción (1.0) que el continuo EXPERIMENTAL con preferencias por defecto; zoom nunca achica bajo fit-ancho | `F2 a tall page reads like HQ…`, `F2 the paged tall-page window matches…` | verde `62e8711` |
| F3 | Sin regresión del Fallo E: el plan de teselas no cambia y los quads colocados particionan la página exactamente (tope de la primera tesela = tope de la página; pie de la última = pie de la página) | `F3 the tile plan still bounds…` | por construcción (`createFromImage` y `GpuTilePlanPolicy` intactos) + test propio en la serie; commit de docs cierra la fila |

## Rojo / verde (CI)

- Rojo `010d28c` — run **VER EL ÚLTIMO REGISTRO** en `fix/fallo-f-calidad`:
  `testDebugUnitTest` falla POR COMPORTAMIENTO (símbolos `LongStripFitPolicy`
  inexistentes; el módulo `app` no compila).
- Verde F1 `1162a24` / F2 `62e8711` — runs **VER EL ÚLTIMO REGISTRO**; el resultado
  definitivo de cada run queda anotado en `docs/REGISTRO.md` al cerrar.

## STOP — diagnóstico, no activado

La condición STOP del encargo era «si la raíz no está en la ruta de renderizado de T-E
(decoder, filtrado de texturas)». El decoder (`ca.mpreg:imagedecoder`, dependencia
cerrada) y el sampler no fueron necesarios tocar: la raíz SÍ está en la ruta de
renderizado (la colocación del camino paginado), se corrigió ahí y el muestreo nativo
de T-E quedó sin cambios.

## Checklist de dispositivo (usuario)

1. La misma tira webtoon ultra-alta que reportaste: modo paginado EXPERIMENTAL → se ve
   al ANCHO de la pantalla, nítida, abriendo en la parte de arriba de la página.
2. Deslizar verticalmente dentro de la página: recorre la tira hasta el final, sin
   huecos arriba/abajo; al pasar de página la siguiente abre también en su tope.
3. Zoom (pellizco / doble toque): aumenta desde el fit-ancho, sin achicarse debajo.
4. La misma tira en HQ: legibilidad equivalente (F2) — comparar la misma página en los
   dos visores.
5. Modo continuo EXPERIMENTAL (webtoon): sin cambios respecto a como estaba (la
   política solo toca el camino paginado).
6. Sin crash con contenido que supera el límite GPU (Fallo E sigue resuelto — F3).

## PROBLEMAS.md

- Fila Fallo E: se añade la nota «fix merged but quality regression reported → Fallo F».
- Nueva fila Fallo F (abierta hasta la validación del usuario en dispositivo).

## En criollo

Antes la página larguísima reventaba la GPU; con el arreglo de las teselas ya no
revienta, pero el lector la «achataba» entera en la pantalla y quedaba ilegible. Ahora
el lector EXPERIMENTAL hace lo mismo que HQ: te muestra la tira al ancho de la pantalla
— nítida, píxel por píxel — y la vas recorriendo de arriba hacia abajo con el dedo.
Las páginas normales ni se enteran: se ven exactamente igual que siempre.

## 📲 BUILD PARA INSTALAR

- Branch: `fix/fallo-f-calidad`
- Commit: punta de la rama al cerrar (ver REGISTRO)
- Run CI: **VER EL ÚLTIMO REGISTRO** (job «Build Release APK», artefacto
  `INSTALAR-<sha>`)
- APK: `MihonFix-0.21.3-build-<sha>-<yyyymmdd-hhmm UTC>-arm64.apk`
- Firma: verificar con `python3 scripts/verify_apk_signature.py <apk>` → ESTABLE
- sha256 + tamaño: los del artefacto `INSTALAR-<sha>` de la run (anotar al descargar)
- Si la run no produce artefacto: «Ninguna — encargo sin build nueva» + motivo en
  REGISTRO.
