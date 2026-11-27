# INFORME T-E — Fallo E: textura 800×50000 > límite GPU (visor EXPERIMENTAL WebGPU)

Rama: `fix/fallo-e-textura` (cortada de `main` @ `bd4aa16e6`, post-merge PR #14).
Encargo: PROMPT 9, tarea T-E. Fecha: 2026-11-27. Agente: GLM.

## Problema

El usuario reportó (2026-11-26/27) un `ValidationException` al leer tiras webtoon largas
en el visor EXPERIMENTAL WebGPU: el contenido alto se componía en UNA textura gigante
(p.ej. 800×50000) y `createTexture` lanza cuando la dimensión supera el límite del
dispositivo — `maxTextureDimension2D` = 16384 en la mayoría de GPUs, 8192 en algunas.
El código ya pedía 16384 en `requiredLimits` (con reintento a defaults del adapter), pero
ningún límite basta para una tira de 50000 px: el fallo era de diseño (una textura por
frame), no de configuración.

Condición STOP verificada: el fallo ES el límite de textura (el mensaje del usuario nombra
`ValidationException` en `CreateTexture` con dimensiones > 16384) y afecta SOLO al visor
EXPERIMENTAL WebGPU — STOCK/HQ no usan WebGPU. No se activó el STOP.

## Solución

Trocear el contenido sobredimensionado en teselas ordenadas (una textura por tesela, cada
una ≤ límite real del device), sin escalar y sin pérdida de calidad; el draw pinta un quad
NDC por tesela. Misma disciplina de capas que `ContinuousPageReportPolicy`: la decisión
pura (plan de teselas + mapeo NDC) es una policy JVM-testable; el wiring GPU queda
delimitado al renderer.

## Comportamiento → test → commit

| ID | Comportamiento | Test (JVM) | Commit |
| --- | --- | --- | --- |
| E1 | Plan de teselas: dado (ancho, alto, límite) → lista ordenada; 800×50000 @8192 → 8 teselas (7×8192+2656); @16384 → 4 (3×16384+848); nunca supera el límite; sin huecos ni solapes; múltiplos exactos sin celda vacía; sin escalado | `GpuTilePlanPolicyTest` (`E1 ...`, 5 tests) | rojo `40382a0e5` → verde `353313c81` |
| E2 | Wiring: `createFromImage` consulta `device.limits.maxTextureDimension2D` (fallback 16384), planifica y crea UNA textura por tesela (toda petición ≤ límite); `uploadTilePixels` copia la banda exacta; `drawPlacedPages` (continuo) y `drawSinglePage` (paginado con zoom/pan) expanden a un quad por tesela con la MISMA matemática; destroy/stop cierran todas las texturas | cobertura por E1+E3 (el wiring GPU no es ejecutable en JVM; validación de dispositivo en el checklist) | `0ecafd2cf` |
| E3 | Regresión cero: página normal = 1 tesela = el quad de siempre bit a bit; quads de teselas parten el quad de página exactamente (sin costura, sin solape, orden arriba→abajo) | `GpuTilePlanPolicyTest` (`E3 single-tile...`, `E3 tiled quads...`, `E3 normal pages...`, `E3 degenerate...`) | `53c7c69d1` (matemática extraída a `GpuTilePlanPolicy.tileQuad`, una sola implementación) |

Commits de formato exigidos por ktlint: `7a87c2c0d` (indentación del test). No son parte
del conteo rojo/verde (regla del encargo: fallo de formato no cuenta como rojo).

## Rojo / verde (CI)

- Rojo `40382a0e5` — run 45946738209: los 10 tests E1–E3 fallan sobre el esqueleto no-op
  (plan vacío) POR COMPORTAMIENTO; compile OK, Spotless OK. El run figura «cancelled» en
  GitHub porque lo canceló el push siguiente del mismo actor, pero sus jobs de test ya
  habían fallado (Spotless & Unit Tests failure, job 140334291019) — el detalle de
  violación que traía era SOLO formato del test (indentación), corregido en `7a87c2c0d`.
- Verde E1 `353313c81` — run 45946758382: E1 verde; el resto de jobs falló por la misma
  indentación del test (ya corregida).
- Verde E2+E3 `0ecafd2cf` / `53c7c69d1` — runs 45952813399 / 45952895809 (en curso al
  redactar; se anota su resultado en REGISTRO al cerrar).

## Disciplina de alcance

Solo se tocaron archivos del visor WebGPU (`GpuTilePlanPolicy.kt` nuevo,
`AndroidxWebGpuSurfaceRenderer.kt`, su test). Nada de Bloque B (swipe zoom, destroy de
texturas, N2, WebP, quick wins), nada de STOCK/HQ, nada del reparador.

## Checklist de dispositivo (usuario)

1. Tira webtoon ultra-alta (la de 800×50000 u otra) en modo continuo EXPERIMENTAL:
   renderiza completa, sin crash, sin franjas vacías ni duplicadas entre teselas.
2. Misma tira en paginado L2R/R2L/vertical EXPERIMENTAL: página única normal, zoom/pan
   intactos.
3. Páginas normales: idénticas a antes (una textura, un quad).
4. Orden de teselas: arriba→abajo sin saltos al hacer scroll.

## En criollo

Antes, el lector WebGPU intentaba meter una tira de cómic de 50000 píxeles de alto en una
sola textura gigante y la GPU decía «no me entra» (su límite es 16384) y la app reventaba.
Ahora pregunta a la GPU «¿cuánto te cabe?» y corta la tira en rebanadas que sí caben, en
orden, y las pega en pantalla tal cual — sin achicar ni reescalar nada. Las páginas
normales ni se enteran: siguen siendo una sola rebanada, la misma de siempre.
