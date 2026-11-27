# INFORME T-B — Botón «Reintentar títulos con error» (UpdateErrorsScreen)

Rama: `feat/p5-reintentar-titulos-error` (cortada de `main` @ `bd4aa16e6`, post-merge PR #14).
Encargo: PROMPT 9, tarea T-B. Fecha: 2026-11-27. Agente: GLM.

## Contexto y hechos auditados (verificados contra el código antes de empezar)

- `UpdateErrorsScreen`/`UpdateErrorsViewModel` existen en `mihon/feature/library/errors/`. ✔
- `UpdateErrorStore.getErrorMangaIds(): Set<Long>` existe (línea 56). ✔
- `replaceForManga(newErrors, processedIds)` soporta lotes parciales (línea 80) — aunque el
  botón no lo necesita: ver 5.3. ✔
- «Con error de actualización» es clasificación manual, no filtro; los filtros reales son 6.
  No se tocó ninguno. ✔
- `UpdateMangaFromRemote` en su rama de éxito llama
  `updateErrorStore.removeErrorsForManga(manga.id)` (auto-limpieza por manga): los éxitos
  dejan la lista solos y `errorsFlow` refresca la pantalla. ✔
- `calm pass` (B3), `RepairOutcomePolicy`, `CalmPass`: intactos (5.5, la policy es inerte).

## Comportamiento → test → commit

| ID | Comportamiento | Test (JVM) | Commit |
| --- | --- | --- | --- |
| 5.1 | Botón visible solo con ≥1 error; etiqueta = string resource | `RetryErrorsPolicyTest` `5_1` | rojo `d476a2416` → verde `ef16916fb` (policy) + wiring `c55de1e87` |
| 5.2 | Alcance: solo el snapshot de `getErrorMangaIds()` al pulsar; nunca biblioteca/categoría/filtro | `5_2` (×2) | `ef16916fb` |
| 5.3 | Éxitos desaparecen al instante (auto-limpieza + errorsFlow); fallos quedan con error fresco | `5_3` | `fad775114` |
| 5.4 | Botón deshabilitado durante el lote; un lote por pulsación; al terminar recupera; todo-éxito → estado vacío | `5_4` (×2) | `fad775114` |
| 5.5 | Cero regresión: solo las costuras auditadas; policy inerte (B3/RepairOutcomePolicy/CalmPass sin tocar) | `5_5` | `fad775114` (SEAMS) |

Wiring (Compose): botón `TextButton` con `testTag("update_errors_retry_button")` en la
AppBar de UpdateErrorsScreen, string `update_errors_retry` (base: «Retry titles with
errors» / es: «Reintentar títulos con error») y `update_errors_retry_running` («Reintentando…»).
`UpdateErrorsViewModel.retryErrorMangas()` ejecuta el lote en `Dispatchers.IO`.

## Rojo / verde (CI)

- Rojo `d476a2416`: esqueleto «el botón no existe» + 8 tests; corre en CI (run a anotar en
  REGISTRO al cerrar).
- Verde `ef16916fb` (5.1–5.2), `fad775114` (5.3–5.5), `c55de1e87` (wiring UI): runs en CI.

## Qué NO hace (alcance)

- No usa `LibraryUpdateWorker` (eso sería actualización de biblioteca por fuente/categoría —
  fuera de alcance); el reintento es directo por interactors.
- No toca el registro/categorización de errores, ni la pasada en calma, ni los filtros.
- `replaceForManga` no se invoca: la auto-limpieza por manga ya cubre 5.3 (el store se
  actualiza manga a manga dentro de `UpdateMangaFromRemote`).

## Checklist de dispositivo (usuario)

1. Con ≥1 título con error: botón visible arriba; con lista vacía: no aparece (5.1).
2. Pulsar → «Reintentando…» y botón deshabilitado; una sola tanda aunque se machaque (5.4).
3. Títulos que ahora actualizan bien desaparecen de la lista al instante; los que siguen
   rotos se quedan con su error nuevo/refresh (5.3).
4. Si todos salen bien → pantalla queda en estado vacío (5.4).
5. La pasada en calma y los informes de fuentes problemáticas siguen igual (5.5).

## En criollo

Arriba de la pantalla de errores ahora hay un botón «Reintentar títulos con error». Le das,
y la app vuelve a intentar actualizar SOLO los que están en la lista de errores en ese
momento — no toda la biblioteca. Los que ya funcionan se salen de la lista al momento; los
que siguen malos se quedan ahí con su error nuevo. Mientras retrya no podés meter otra
tanda: el botón se apaga hasta que termina.
