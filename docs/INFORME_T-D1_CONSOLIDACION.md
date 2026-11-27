# INFORME T-D1 — Consolidación documental 2026-11-27

Rama: `docs/consolidacion-2026-11-27` (cortada de `main` @ `bd4aa16e6`). Encargo: PROMPT 9
(T-D1) + PROMPT 9-ADD. Fecha: 2026-11-27. Agente: GLM. PR: #19.

## Entregables → commit

| Requisito del encargo | Dónde quedó | Commit |
| --- | --- | --- |
| Fallo C cerrado (confirmación del usuario 2026-11-27; causa = error del usuario al elegir archivo/APK) | `docs/PROBLEMAS.md` — fila cerrada, contenido forense conservado como historia | `df424a5ee` |
| Nueva fila «Fallo E» (abierta; ValidationException textura 800×50000 > límite GPU 16384×16384, visor experimental WebGPU, reporte 2026-11-26/27, disposición = T-E) | `docs/PROBLEMAS.md` | `df424a5ee` |
| Ambas filas F4 anotadas «cierre previsto con la validación de dispositivo de P7 (W1/W2/W4) — mismas rutas WebView»; NO cerradas | `docs/PROBLEMAS.md` (2 filas, siguen Abierto) | `df424a5ee` |
| AGENTS.md §2: nombre de APK corregido a `MihonFix-0.21.3-build-<timestamp>-arm64.apk` (V2) | `AGENTS.md` | `df424a5ee` |
| AGENTS.md §3: tabla de ramas refrescada a la realidad (main punta `bd4aa16e` post-PR #14; T6 mergeada vía PR #13; P8/T-D1/T-E/T-B vivas; histórico marcado) | `AGENTS.md` | `df424a5ee` |
| `docs/ADOPCIONES_UPSTREAM.md` nuevo: tabla de 6 filas verificadas (rebase v0.21.4; PR #3786 WebP ruta b; revisar caché upstream; pins CI setup-java v6.3.0 / setup-gradle v6.4.1 / ubuntu-26.04; port extensiones embebidas MHScans/Emperor Scan; recetas comunidad #4309/#3017/#4305/#3882/#4188/#4220/#4272/#4106/#4147/#4310/#4301/#4246/#565+#3661) + nota «pendiente de aportaciones del usuario» | `docs/ADOPCIONES_UPSTREAM.md` | `df424a5ee` |
| REGISTRO: cierre Fallo C, apertura Fallo E, decisión W3 (sin veto → la exención sin conectividad SE QUEDA), decisión 1b = C+ (informe perdido NO se reconstruye ni se re-audita; nota honesta con la evidencia viva: PRs #8–#13, 35 tests B1–B4, `INFORME_REPARADOR_ENLACES_B1-B4.md`, políticas), V1–V8 según lo DECIDIDO por el usuario en el ADD (V1/V4/V6/V7/V8 ratificadas; V2 corregida aquí; V3 corregida aquí; V5 aceptada como limitación conocida — nada inventado) | `docs/REGISTRO.md` | `df424a5ee` |
| V3 (única excepción autorizada a workflows): prefijo `NO-INSTALAR-` a `test-report-` y `mapping-` en `build.yml`, sin cambiar lógica | `.github/workflows/build.yml` | `169aa04d2` (commit `ci:` separado) |

## Estructura de commits

Dos commits, como exige el ADD: `169aa04d2` (ci: V3) y `df424a5ee` (docs: todo lo demás).
El encargo permitía un commit único de docs; V3 iba separado por diseño («single ci commit»).

## CI

Run 45954074642 sobre `df424a5ee` (punta): **success** — los 6 jobs en verde. El commit
`169aa04d2` corrió dentro de la misma serie (runs previos de la rama).

## Disciplina

- PROBLEMAS.md: solo la fila Fallo C se cerró y solo se añadió Fallo E + las dos frases F4
  — ninguna otra fila tocada.
- No se tocaron: extensiones, RepairOutcomePolicy/CalmPass/B3, `verify_apk_signature.py`,
  ramas históricas. `build.yml` solo con el cambio V3 autorizado.
- docs: todas las entradas previas de REGISTRO.md conservadas (solo se añadió).

## En criollo

Papeles al día: cerramos el «Fallo C» porque el usuario confirmó que la instalación encima
ya andaba hace rato (fue un error suyo eligiendo archivo, no de la app); abrimos el
«Fallo E» (la tira larguima que revienta la GPU); anotamos que los dos pendientes del
reparador se cierran cuando el usuario valide P7 en su teléfono; ordenamos la tabla de
ramas; creamos la lista de cosas del upstream que conviene adoptar; y dejamos asentadas
todas las decisiones que el usuario tomó (qué hacer con el informe perdido, las
desviaciones V1–V8, y el renombrado de artefactos de CI para que nadie confunda un
mapping con un APK).

## 📲 BUILD PARA INSTALAR

**Ninguna — encargo sin build nueva.** T-D1 es un encargo 100 % documental (dos commits:
`ci:` de prefijos de artefactos y `docs:`): no hay cambios de código de la app, así que la
primera build instalable de esta rama es la que salga del merge del PR #19 si el usuario
lo ordena. Las builds instalables del bloque siguen estando en las ramas de código
(T-E, T-B, P8, T-REB) — ver el resumen del bloque.
