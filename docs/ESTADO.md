# ESTADO.md — diagnóstico del repositorio (solo lectura)

> **Diagnóstico solo lectura, 2026-11-27 01:35 UTC (UTC).** Encargo PROMPT-D. Generado por el agente a
> petición del usuario: sin commits, sin push, sin merges, sin cambios de código. Único
> archivo escrito: este `docs/ESTADO.md` (sin commit — queda como archivo nuevo sin seguimiento).
> Fuente: `git` local (clon fresco de `origin` a fecha del diagnóstico) + API de GitHub
> (`/pulls?state=all`, `/actions/runs`, logs de jobs). Ningún dato es estimado.

## 1) Ramas (todas, con tip sha)

| Rama | Tip | Tema |
|---|---|---|
| `main` (HEAD) | `7d1092685` | cierre de T6 (PR #13) |
| `ci/branch-checks` | `a6ba61507` | verificación de branch checks (revert final) |
| `ci/nomenclatura-builds` | `9bba91f72` | nomenclatura INSTALAR-/NO-INSTALAR- |
| `docs/coherencia-post-auditoria-reparador` | `509c1c656` | docs reparador |
| `docs/post-merge-auditoria-reparador` | `03d09fdf0` | docs reparador |
| `docs/registro-fallos-c-d` | `c39eece62` | Fallo C / Fallo D |
| `feat/p7-webview-fallback-all-failures` | `682889285` | **P7 en curso** — commit rojo (policy + tests) |
| `feat/reader-page-state-wiring` | `fd0f2d000` | checklist T6 |
| `feat/t6-page-state-machine-wiring` | `8926a5a28` | T6 (ya mergeada en main) |
| `feature/webgpu-clean` | `736fa6baa` | línea histórica del visor |
| `fix/migration-guard-fixes` | `e70f38316` | guards de migración |
| `fix/reparador-enlaces-tests` | `4c5f15155` | tests B1-B4 |
| `fix/viewer-refactor-t1-t7` | `fa91a3923` | visor T1-T7 |

## 2) Últimos 25 commits de `origin/main`

```
7d1092685 2026-11-26 merge: feat/t6-page-state-machine-wiring — cierre de T6 (PR #13)
8926a5a28 2026-11-26 docs(reader): cierre de T6 parte 3/3 — CHANGELOG
7f61edb6e 2026-11-26 docs(reader): cierre de T6 parte 2/3 — REGISTRO
c6e1e129b 2026-11-26 docs(reader): T6 cierre — informe + PROBLEMAS (T6-C1, 1/3)
d017b3f2c 2026-11-26 Merge PR #12: ci: nomenclatura de builds
9bba91f72 2026-11-26 docs: hora UTC real de la entrada de nomenclatura (04:55-04:58)
7a721207e 2026-11-26 docs: convención «Qué build instalar» en AGENTS.md §5.4
b0889e26a 2026-11-26 ci: nomenclatura de builds — INSTALAR-/NO-INSTALAR-
3d3b602c4 2026-11-26 docs: registro de los merges PR #10 y PR #11
127dfd54d 2026-11-26 Merge PR #11: docs: registro de Fallo C y Fallo D
3ac7f98a2 2026-11-26 Merge PR #10: docs: cierre post-merge auditoría reparador (F6)
03d09fdf0 2026-11-26 docs: CHANGELOG — merges PR #8 (F1/F2/F3) y PR #9 (F5)
e138a667c 2026-11-26 docs: informe B1-B4 regenerado con datos verificados
0eaa894e7 2026-11-26 docs: PROBLEMAS — B1 cerrado por decisión del usuario
c39eece62 2026-11-26 docs: registro de Fallo C y Fallo D
0c80777a3 2026-11-26 docs: cierre post-merge auditoría reparador (F6)
00f465d6c 2026-11-25 Merge PR #9: fix(reparador): tests B1-B4 (F4/F5)
b5a132331 2026-11-25 Merge PR #8: docs: coherencia post-auditoría reparador
4c5f15155 2026-11-25 docs: registro del encargo B1-B4
03fe7242c 2026-11-25 style(reparador): shorten budget-boundary lines
5eb655fa1 2026-11-25 style(reparador): ktlint format
6b7833657 2026-11-25 fix(reparador): qualify budget constant (compile fix)
8898fd579 2026-11-25 refactor(reparador): calm-pass pure policy (B3, F4)
fb4701201 2026-11-25 refactor(reparador): resolver outcome fold pure policy (B4, F5)
9b8be0de2 2026-11-25 style(reparador): ktlint format store tests
```

## 3) PRs (abiertos: 0; cerrados/mergeados: recientes)

| PR | Estado | Rama → destino | Merge sha | Cuándo (UTC) | Título |
|---|---|---|---|---|---|
| #13 | merged | `feat/t6-page-state-machine-wiring` → main | `7d1092685` | 2026-11-26 22:44 | cierre: T6 — máquina de estados cableada + fix |
| #12 | merged | `ci/nomenclatura-builds` → main | `d017b3f2c` | 2026-11-26 05:13 | nomenclatura de builds |
| #11 | merged | `docs/registro-fallos-c-d` → main | `127dfd54d` | 2026-11-26 04:42 | Fallo C y Fallo D |
| #10 | merged | `docs/post-merge-auditoria-reparador` → main | `3ac7f98a2` | 2026-11-26 04:40 | cierre post-merge auditoría (F6) |
| #9 | merged | `fix/reparador-enlaces-tests` → main | `00f465d6c` | 2026-11-26 00:07 | tests B1-B4 (F4/F5) |
| #8 | merged | `docs/coherencia-post-auditoria-reparador` → main | `b5a132331` | 2026-11-26 00:06 | coherencia post-auditoría |
| #7 | merged (11-17) | `fix/viewer-y-migracion` → feature/webgpu-clean | `bee9c6490` | — | selector único de visor + guards |
| #6 | closed sin merge | `fix/migration-guards` → feature/webgpu-clean | — | — | guards de migración |
| #5 | merged (11-17) | `fix/update-without-uninstall-docs` → feature/webgpu-clean | `98d02a6fa` | — | sin desinstalar |
| #4 | closed sin merge | `fix/webgpu-migration-metadata` | — | — | metadatos migración |
| #3 | closed sin merge | `fix/webgpu-viewer-modes` | — | — | visor webgpu |
| #2 | merged (11-17) | `docs/agents-md` → main | `b8ab1c52a` | — | AGENTS.md |
| #1 | merged (11-17) | `fix/webview-fetch-pr1` → main | `3b59c88b9` | — | WebView fetch fallback |

**No hay PRs abiertos a fecha del diagnóstico** (la rama P7 aún no tiene PR; el commit rojo
está pusheado pero el PR se abrirá con la serie completa según el encargo PROMPT 7).

## 4) CI runs de los últimos 3 días (2026-11-24 → 2026-11-27)

Resumen: **100 runs** (Build & Test + Build APK). Verdes en `main`: la punta `7d1092685`
tiene doble verde (22:44). Serie red-green visibles en T6 y reparador. La única run de la
rama P7 es la roja intencional del commit `682889285`.

| Run id | Fecha (UTC) | Workflow | Rama | Commit | Resultado |
|---|---|---|---|---|---|
| 45470833586 | 11-24 19:29 | Build & Test | main | e4dcc2e43 | success |
| 45470833364 | 11-24 19:29 | Build APK | main | e4dcc2e43 | success |
| 45472880853 | 11-24 19:41 | Build & Test | main | aada310bb | success |
| 45472880744 | 11-24 19:41 | Build APK | main | aada310bb | success |
| 45496314551 | 11-24 21:58 | Build APK | ci/branch-checks | 4c4bda34e | success |
| 45497174356 | 11-24 22:03 | Build APK | ci/branch-checks | 1f0925e8f | failure (spotless, intencional) |
| 45498398483 | 11-24 22:10 | Build APK | ci/branch-checks | a6ba61507 | success (revert) |
| 45498403676 | 11-24 22:10 | Build & Test | main | ca86c5540 | cancelled |
| 45498403597 | 11-24 22:10 | Build APK | main | ca86c5540 | cancelled |
| 45498453859 | 11-24 22:10 | Build APK | main | 4b0c11f29 | success |
| 45498453839 | 11-24 22:10 | Build & Test | main | 4b0c11f29 | success |
| 45501686573 | 11-24 22:31 | Build APK | main | 60ba74d9c | cancelled |
| 45501686282 | 11-24 22:31 | Build & Test | main | 60ba74d9c | cancelled |
| 45501735961 | 11-24 22:31 | Build & Test | main | cd49eb442 | success |
| 45501735941 | 11-24 22:31 | Build APK | main | cd49eb442 | success |
| 45503634506 | 11-24 22:43 | Build APK | main | 7a1fcdc96 | success |
| 45503634302 | 11-24 22:43 | Build & Test | main | 7a1fcdc96 | success |
| 45510187022 | 11-24 23:27 | Build APK | fix/migration-guard-fixes | 89e23a0af | failure |
| 45511405846 | 11-24 23:36 | Build APK | fix/migration-guard-fixes | 01bda2c0f | failure |
| 45512452677 | 11-24 23:44 | Build APK | fix/migration-guard-fixes | 924f41e3b | failure |
| 45513628621 | 11-24 23:52 | Build APK | fix/migration-guard-fixes | 339019d8d | failure |
| 45514750075 | 11-25 00:01 | Build APK | fix/migration-guard-fixes | c04bd69d0 | failure |
| 45515895797 | 11-25 00:09 | Build APK | fix/migration-guard-fixes | 800c2c9fc | failure |
| 45517009845 | 11-25 00:18 | Build APK | fix/migration-guard-fixes | b9ea139a5 | success |
| 45518171941 | 11-25 00:27 | Build APK | fix/migration-guard-fixes | c3818df3e | failure |
| 45519304514 | 11-25 00:35 | Build APK | fix/migration-guard-fixes | a53d7c473 | failure |
| 45520386869 | 11-25 00:44 | Build APK | fix/migration-guard-fixes | 6f58a5e26 | failure |
| 45521510619 | 11-25 00:52 | Build APK | fix/migration-guard-fixes | e734e1c94 | success |
| 45526075412 | 11-25 01:29 | Build APK | fix/migration-guard-fixes | 8ff2e5d19 | failure |
| 45527137743 | 11-25 01:37 | Build APK | fix/migration-guard-fixes | b1001ded0 | failure |
| 45530795651 | 11-25 02:08 | Build APK | fix/migration-guard-fixes | ca6978b6d | cancelled |
| 45530892364 | 11-25 02:09 | Build APK | fix/migration-guard-fixes | 23b421d10 | failure |
| 45531902795 | 11-25 02:17 | Build APK | fix/migration-guard-fixes | e49247347 | failure |
| 45532973243 | 11-25 02:26 | Build APK | fix/migration-guard-fixes | 78a5cbc3d | failure |
| 45533995656 | 11-25 02:35 | Build APK | fix/migration-guard-fixes | 2660276f6 | failure |
| 45535017470 | 11-25 02:43 | Build APK | fix/migration-guard-fixes | eb57b9b69 | failure |
| 45536071798 | 11-25 02:52 | Build APK | fix/migration-guard-fixes | e106b71fc | success |
| 45539797392 | 11-25 03:24 | Build APK | fix/migration-guard-fixes | f94c94263 | failure |
| 45540772436 | 11-25 03:33 | Build APK | fix/migration-guard-fixes | 8cbe04c02 | success |
| 45541788099 | 11-25 03:42 | Build APK | fix/migration-guard-fixes | e70f38316 | success |
| 45542703056 | 11-25 03:50 | Build & Test | main | 152027f99 | success |
| 45542702890 | 11-25 03:50 | Build APK | main | 152027f99 | success |
| 45544710660 | 11-25 04:08 | Build APK | feat/reader-page-state-wiring | 6e92a8685 | failure |
| 45545753032 | 11-25 04:17 | Build APK | feat/reader-page-state-wiring | 676008ee4 | failure |
| 45546708415 | 11-25 04:25 | Build APK | feat/reader-page-state-wiring | 45288c607 | failure |
| 45547657817 | 11-25 04:34 | Build APK | feat/reader-page-state-wiring | d48f488aa | failure |
| 45548592650 | 11-25 04:42 | Build APK | feat/reader-page-state-wiring | 1360c9562 | failure |
| 45549538159 | 11-25 04:50 | Build APK | feat/reader-page-state-wiring | ebe602bdb | failure |
| 45550672404 | 11-25 05:01 | Build APK | feat/reader-page-state-wiring | 58bf0a01f | failure |
| 45551691237 | 11-25 05:10 | Build APK | feat/reader-page-state-wiring | f520f7796 | success |
| 45552919057 | 11-25 05:20 | Build APK | feat/reader-page-state-wiring | fd0f2d000 | success |
| 45647679860 | 11-25 15:39 | Build APK | feat/t6-page-state-machine-wiring | c2de25f6e | success |
| 45649632312 | 11-25 15:54 | Build APK | feat/t6-page-state-machine-wiring | 0c394887b | success |
| 45667997696 | 11-25 17:44 | Build APK | feat/t6-page-state-machine-wiring | b44029d1b | failure (rojo R1 por comportamiento) |
| 45669472893 | 11-25 17:53 | Build APK | feat/t6-page-state-machine-wiring | 2f733e4bc | failure (rojo R1) |
| 45670956193 | 11-25 18:02 | Build APK | feat/t6-page-state-machine-wiring | d3255b6d5 | failure (rojo R1, alineación) |
| 45672474212 | 11-25 18:10 | Build APK | feat/t6-page-state-machine-wiring | 083edad9d | success |
| 45673989574 | 11-25 18:19 | Build APK | feat/t6-page-state-machine-wiring | 362c432b1 | success |
| 45675621395 | 11-25 18:29 | Build APK | feat/t6-page-state-machine-wiring | d37226358 | success |
| 45693694736 | 11-25 20:18 | Build APK | feat/t6-page-state-machine-wiring | 6b60b5b46 | failure (rojo R3 por formato) |
| 45695265725 | 11-25 20:28 | Build APK | feat/t6-page-state-machine-wiring | d75b6e43e | failure (rojo R3 por comportamiento) |
| 45696649989 | 11-25 20:37 | Build APK | feat/t6-page-state-machine-wiring | e3ade9cba | success |
| 45698019408 | 11-25 20:45 | Build APK | feat/t6-page-state-machine-wiring | ce3db2d0a | success |
| 45699495988 | 11-25 20:55 | Build APK | feat/t6-page-state-machine-wiring | 12703d34f | success |
| 45710521222 | 11-25 22:07 | Build APK | docs/coherencia-post-auditoria-reparador | 509c1c656 | success |
| 45711734737 | 11-25 22:15 | Build & Test | docs/coherencia-post-auditoria-reparador | 509c1c656 | success |
| 45714120610 | 11-25 22:32 | Build APK | fix/reparador-enlaces-tests | f4f30bc9a | failure |
| 45715318646 | 11-25 22:41 | Build APK | fix/reparador-enlaces-tests | 9b8be0de2 | success |
| 45717118756 | 11-25 22:54 | Build APK | fix/reparador-enlaces-tests | fb4701201 | success |
| 45718385150 | 11-25 23:03 | Build APK | fix/reparador-enlaces-tests | 8898fd579 | failure |
| 45719531542 | 11-25 23:12 | Build APK | fix/reparador-enlaces-tests | 6b7833657 | failure |
| 45720656549 | 11-25 23:20 | Build APK | fix/reparador-enlaces-tests | 5eb655fa1 | failure |
| 45721723015 | 11-25 23:29 | Build APK | fix/reparador-enlaces-tests | 03fe7242c | success |
| 45722806907 | 11-25 23:37 | Build APK | fix/reparador-enlaces-tests | 4c5f15155 | success |
| 45722832669 | 11-25 23:37 | Build & Test | fix/reparador-enlaces-tests | 4c5f15155 | success |
| 45726323953 | 11-26 00:06 | Build APK | main | b5a132331 | cancelled |
| 45726323898 | 11-26 00:06 | Build & Test | main | b5a132331 | cancelled |
| 45726397373 | 11-26 00:07 | Build & Test | main | 00f465d6c | success |
| 45726397560 | 11-26 00:07 | Build APK | main | 00f465d6c | success |
| 45727096991 | 11-26 00:12 | Build APK | docs/post-merge-auditoria-reparador | 0c80777a3 | success |
| 45744144354 | 11-26 02:41 | Build APK | docs/registro-fallos-c-d | c39eece62 | success |
| 45755881105 | 11-26 04:32 | Build APK | docs/post-merge-auditoria-reparador | 03d09fdf0 | success |
| 45756725263 | 11-26 04:40 | Build APK | main | 3ac7f98a2 | cancelled |
| 45756725042 | 11-26 04:40 | Build & Test | main | 3ac7f98a2 | cancelled |
| 45756884878 | 11-26 04:42 | Build & Test | main | 127dfd54d | success |
| 45756884870 | 11-26 04:42 | Build APK | main | 127dfd54d | success |
| 45758422555 | 11-26 04:57 | Build APK | main | 3d3b602c4 | success |
| 45758422542 | 11-26 04:57 | Build & Test | main | 3d3b602c4 | success |
| 45758524307 | 11-26 04:58 | Build APK | ci/nomenclatura-builds | b0889e26a | cancelled |
| 45758582660 | 11-26 04:58 | Build APK | ci/nomenclatura-builds | 7a721207e | cancelled |
| 45758708523 | 11-26 05:00 | Build APK | ci/nomenclatura-builds | 9bba91f72 | success |
| 45758749037 | 11-26 05:00 | Build & Test | ci/nomenclatura-builds | 9bba91f72 | success |
| 45760223904 | 11-26 05:13 | Build APK | main | d017b3f2c | success |
| 45760223726 | 11-26 05:13 | Build & Test | main | d017b3f2c | success |
| 45899394987 | 11-26 21:37 | Build APK | feat/t6-page-state-machine-wiring | c6e1e129b | success |
| 45904983836 | 11-26 22:28 | Build APK | feat/t6-page-state-machine-wiring | 7f61edb6e | cancelled |
| 45904996809 | 11-26 22:28 | Build APK | feat/t6-page-state-machine-wiring | 8926a5a28 | success |
| 45906664795 | 11-26 22:44 | Build & Test | main | 7d1092685 | success |
| 45906664786 | 11-26 22:44 | Build APK | main | 7d1092685 | success |
| 45918320778 | 11-27 00:42 | Build APK | feat/p7-webview-fallback-all-failures | 682889285 | failure (ROJO P7 por comportamiento: 9 tests W1-W5; Spotless OK; APK release OK) |


## 5) Rama `feat/p5-reintentar-titulos-error`

**No existe** en el remoto (`git branch -a` y API no la listan). Tampoco existe localmente
en ningún clon. Si el encargo P5 se ejecutó, su rama fue borrada o nunca se creó; no hay
commits ni estado de tests que reportar. *(Dato objetivo, sin especular sobre el motivo.)*

## 6) ¿`feat/t6-page-state-machine-wiring` está mergeada en `main`?

**Sí.** PR #13, merge commit **`7d1092685`** («merge: feat/t6-page-state-machine-wiring —
cierre de T6 (PR #13)», 2026-11-26 22:44 UTC), que es la punta actual de `main`.
Doble verde en CI sobre ese sha (runs 45906664795 / 45906664786).

## 7) Archivos en `docs/` añadidos o modificados el 2026-11-26 (en `main`)

| Archivo | Commit que lo tocó |
|---|---|
| `docs/INFORME_REPARADOR_ENLACES_B1-B4.md` | `e138a667c` |
| `docs/INFORME_VISOR_T1-T7.md` | `c6e1e129b` |
| `docs/PROBLEMAS.md` | `c6e1e129b` (también `0eaa894e7`, `c39eece62`) |
| `docs/REGISTRO.md` | `7f61edb6e` (también `509c1c656`, `c39eece62`) |

(Todos vía commits ya mergeados a `main`; `AGENTS.md` también cambió el 26 — `7a721207e` —
pero vive en la raíz del repo, no en `docs/`.)

## Nota de contexto (P7, sin alterar nada)

El commit rojo de P7 (`682889285`, rama `feat/p7-webview-fallback-all-failures`) tiene CI:
run **45918320778** — compilación correcta, Spotless OK, APK release OK, y **9 tests de
`WebViewFallbackPolicyTest` fallando POR COMPORTAMIENTO** (exactamente los previstos W1-W5,
tabla abajo). Es el estado rojo esperado antes de los commits verdes.

| W1 any 4xx code triggers the fallback | 37 | esperaba `true` para 404/429/… — baseline solo dispara {403,503} | W1 |
| W1 any 5xx code triggers the fallback | 46 | esperaba `true` para 500/502/520/… | W1 |
| W2 a network exception triggers the fallback | 76 | esperaba `true` con cause=IOException — baseline nunca dispara por excepción | W2 |
| W2 a socket timeout triggers the fallback | 83 | esperaba `true` con cause=SocketTimeoutException | W2 |
| W3 an HTTP error without connectivity does not trigger | 101 | esperaba `false` sin conectividad — baseline ignora conectividad | W3 |
| W4 N failing requests to a dead host do not parade WebViews | 148 | esperaba 1 intento con código 500 — baseline no dispara con 500 | W4 |
| W4 the cooldown is configurable | 164 | esperaba reintento tras cooldown corto (clock inyectado) con código 500 | W4 |
| W4 distinct hosts do not share the cooldown | 189 | esperaba `true` para otro host con código 500 | W4 |
| W5 the aggregator summarizes attempts per host | 205 | esperaba informe de 2 hosts — baseline: informe vacío (tracker no-op) | W5 |

