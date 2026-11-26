# Informe del reparador de enlaces — encargo B1–B4 (PR #9, merge `00f465d`)

> Regenerado el 2026-11-26 con **solo datos verificados del PR #9** (GitHub: commits,
> checks y runs citados). Fuente de verdad: el propio repo. Nada de lo que sigue fue
> validado en dispositivo — «confirmado» queda reservado a lo que el usuario valide en
> su teléfono. La auditoría que motivó el encargo (hallazgos L/F del 2026-11-26) vive
> en su informe propio; este documento no la reproduce.

## Contexto verificado

- **PR #9** «fix(reparador): tests B1–B4 del reparador de enlaces (F4/F5)», rama
  `fix/reparador-enlaces-tests` cortada de `main` en `152027f`, fusionada a `main`
  con commit de merge **`00f465d`** el 2026-11-26 con instrucción explícita del
  usuario («Procede»), sin borrar la rama.
- **8 commits** en la rama (verificados con la API de pulls): 2 de test/caracterización,
  2 refactor+test (políticas puras), 1 fix de compilación, 2 de estilo, 1 de docs.
  `git rev-list --left-right --count main...fix/reparador-enlaces-tests` al abrir el PR
  daba `0 8`.
- **Alcance declarado y respetado**: cero cambios en workflows, dependencias,
  STOCK/HQ, migración M1–M4 y rama `feat/t6-page-state-machine-wiring`.
- **35 tests nuevos** (240 en total en `:app:testDebugUnitTest`).

## Qué se hizo (comportamiento por comportamiento, con su run)

- **B1+B2 — almacén de errores y clasificación de enlace roto**
  (test/caracterización, `f4f30bc` + `9b8be0d`, run «Build APK»
  [45715318646](https://github.com/kayb28/mihon/actions/runs/45715318646), success):
  13 tests de `UpdateErrorStore` (setErrors / replaceForManga / retainErrors /
  retryCount / remove / clear / round-trip JSON) y de la clasificación (404/410
  acumulan racha ≥3 → enlace roto; otros códigos no).
  **Desfase con la auditoría reportado, no cambiado**: tras confirmarse el enlace
  roto, un código transitorio posterior NO degrada el tipo (semántica «pegajosa») —
  decisión del código original, fijada por test. El usuario la MANTIENE por decisión
  del 2026-11-26 (fila cerrada en `docs/PROBLEMAS.md`).
- **B4 — resolvedor secundario** (`fb47012`, run
  [45717118756](https://github.com/kayb28/mihon/actions/runs/45717118756), success):
  árbol de decisión de `searchRepair` extraído verbatim a `RepairOutcomePolicy`
  (objeto puro JVM) + 13 tests. Solo coincidencias exactas → MATCH_FOUND (reparación
  masiva); fuzzy → NEEDS_REVIEW con score; CROSS_SOURCE_MATCH nunca en lote;
  SEARCH_ERROR ≠ NO_MATCH; una fuente colgada no hunde el ítem si otra dio candidata
  exacta. Equivalencia 1:1 auditada rama por rama (7 caminos) antes de cablear.
- **B3 — pasada en calma** (`8898fd5` refactor + `03fe724` estilo final, run
  [45721723015](https://github.com/kayb28/mihon/actions/runs/45721723015), success):
  decisiones extraídas a `CalmPassPolicy` (pura) + 9 tests: solo fallos transitorios
  van a la calma; origen caído = 3 fallos 5xx consecutivos cortan la cola; presupuesto
  20 min/fuente — lo no intentado se salta SIN error y conserva el suyo vía
  retainErrors. El bucle integrado (WorkManager) no es ejecutable en JVM: así
  declarado en el PR.
- **Coste honesto del ciclo** (round-trips verificados): un fix de compilación
  (`6b78336`, run 45719531542) y dos de formato (`5eb655f`, run 45720656549;
  `9b8be0d` dentro de 45715318646) — runs rojas por estilo/compilación, no por
  comportamiento.

## Runs de la rama (verificadas por API, «Build APK» salvo indicación)

| Commit | Run | Resultado |
| --- | --- | --- |
| `f4f30bc`→`9b8be0d` | 45715318646 | success |
| `fb47012` | 45717118756 | success |
| `8898fd5` | 45718385150 | failure (referencia al literal movido, corregida en `6b78336`) |
| `6b78336` | 45719531542 | failure (formato restante, corregido en `5eb655f`) |
| `5eb655f` | 45720656549 | failure (formato restante, corregido en `03fe724`) |
| `03fe724` | 45721723015 | success |
| `4c5f151` (tip) | 45722806907 + 45722832669 («Build & Test») | success ambas |

## Qué NO se declara

- **Sin red-green**: los 4 comportamientos ya estaban implementados (el hueco era la
  ausencia de tests); los commits de test son caracterización etiquetada honestamente.
- **«Robusto» NO se declara**: verificado en código y CI; la validación en dispositivo
  es del usuario.
- Las rutas WebView y la pasada en calma integrada siguen **pendientes de validación
  en dispositivo** (F4 de la auditoría) — filas abiertas en `docs/PROBLEMAS.md`.
