# Historia consolidada — MihonFix (fork kayb28)

> Última actualización: **2026-11-12 04:20 UTC** · Compilada a partir del histórico de git (151 commits propios), `docs/REGISTRO.md`, `docs/TRASPASO.md`, `CHANGELOG.md` y los manifiestos de trabajo.
>
> Este documento responde a tres preguntas: **qué es este fork, cómo llegó hasta aquí y dónde vive cada cosa.** El detalle operativo día a día sigue en [`docs/REGISTRO.md`](REGISTRO.md) (el diario oficial: todo cambio nuevo se anota ahí), el traspaso técnico extenso en [`docs/TRASPASO.md`](TRASPASO.md) y el changelog formal por build en [`CHANGELOG.md`](../CHANGELOG.md).

## Qué es este fork

- **MihonFix** — fork personal de [mihonapp/mihon](https://github.com/mihonapp/mihon), base **v0.21.3 upstream** (merge-base `e588e0435`), numeración propia `Mihon Fix 0.21.3`, versionCode 39.
- `applicationId` **`app.mihonfix`** (puede convivir instalada junto a la app oficial). Sin Firebase ni telemetría: la CI compila con `-Pinclude-telemetry=false` porque no hay `google-services.json` de cliente.
- **Aportes principales**: reparador de enlaces robusto (almacén de errores sincronizado, resolvedor secundario, motor web de respaldo vía WebView, pasada en calma anti-errores de ráfaga), gestión de fuentes con clasificación propia y agrupación de biblioteca por fuente, visor experimental `androidx.webgpu` (pagado y continuo), extensiones embebidas **MHScans** y **Emperor Scan**, e informes de fuentes problemáticas tras la actualización.
- **Distribución**: sin releases de GitHub. Los APKs viven como artefactos de la CI «Build APK» con nombre `MihonFix-0.21.3-build-<sha>-arm64.apk`. Desde `ef0ece3` (06/11) hay **firma estable**: las builds nuevas se instalan encima de la anterior sin desinstalar.
- Las dos CI del fork: **«Build APK»** (propia, corre en todo push) y **«Build & Test»** (la CI de upstream, corre en push a `main`, ignora `**.md`). `release.yml` y `update_website.yml` están dormidas en el fork (disparan solo con tags `v*` / releases, y la de releases está acotada a `mihonapp/mihon`).

## Datos duros (al 2026-11-12)

| Dato | Valor |
|---|---|
| Commits propios sobre upstream | **151** (`ahead_by`) |
| Upstream avanzó mientras tanto | **20** (`behind_by`) — regla vigente: **no rebasar hasta v0.21.4** |
| Ventana de desarrollo | 2026-10-29 → 2026-11-12 (2 semanas) |
| Identidades distintas en git | **5** (ver tabla siguiente) |
| Ramas tras la consolidación | **2**: `main` y `feature/webgpu-clean` (idénticas en `986aa6ee4`) |
| Ramas archivadas como tags | **6** (`archive/…`, restaurables con `git checkout -b <rama> <tag>`) |
| Tag de seguridad previo | `pre-fix-build-green-20261031` (estado antes de la línea fix/build-green) |
| Build final de este ciclo | `MihonFix-0.21.3-build-f6eb34c8-arm64.apk` (35,2 MB, doble CI verde) |

## Identidades en el histórico (quién es quién)

Git conserva 5 identidades distintas; varias son identidad de sandbox de agentes, no personas separadas. El usuario confirma que casi todo el trabajo del fork se hizo con el modelo GLM-5.3-Flash y el resto con GLM 5.2/5.3 «mayores»; el desglose exacto por modelo no es reconstruible desde git, solo por sesiones:

| Identidad | Commits | Qué hizo | Período |
|---|---|---|---|
| `Z User` | 112 | Identidad de agente de las sesiones largas de `feature/webgpu-clean`: migración WebGPU, estabilización del reparador, docs de ciclo | 01–12 nov |
| `kayb28` | 30 | Pushes del usuario y de sesiones que publicaban a nombre de la cuenta: arranque, línea fix/build-green, madrugada WebGPU del 1–2 nov, resolvedor secundario del 11 nov | 29 oct–12 nov |
| `backup-agent` | 3 | Sesión de gestión de fuentes del 30 oct (integración sobre el reparador, `CAMBIOS_FORK.md`) | 30 oct |
| `Patch-bot` | 3 | Aplicación de parches del 31 oct (reparador, recursos custom, formato) | 31 oct |
| `s9r727asb0ev4G137vo1P` | 3 | La «sesión paralela» del 11–12 nov (rama `fix/review-2026-11-12` → `main`: reintentos, señales secundarias, CI acotada) | 11–12 nov |

## Cronología en seis fases

### Fase 0 — Arranque: fork, unzip.yml y reparador reconstruido (28–30 oct)

- Fork creado el 28 oct. Primeros commits el 29: `753a4b6b4` («Add files via upload») y `d96a1ceec` (workflow `unzip.yml`).
- `048c27152` (30 oct): **reparador de enlaces reconstruido** sobre el `main` actual — el germen de MihonFix.
- Sesión `backup-agent`: telemetría desactivada sin Firebase (`b1d69aeb6`), **integración de gestión de fuentes** sobre el reparador (`5567a8026`) y el manifiesto `CAMBIOS_FORK.md` (`3470c9c5e`).
- `7fb14b591`: port de **agrupación de biblioteca por fuente** (investigada en TachiyomiSY — ver `docs/SOURCE_MANAGEMENT_RESEARCH.md`) sobre la rama `integration`. CI verde (run #12).

### Fase 1 — Línea fix/build-green (31 oct)

- Rama `fix/build-green`: `Patch-bot` aplica parches del reparador (`f8e0d69d2`), recursos custom (`e7aab2158`) y formato (`81a8bb23c`); se restaura el workflow «Build APK» en `main` (`62f394157`) y se completan entradas de `diskLruCache` (`f0b2a69bb`).
- El tag `pre-fix-build-green-20261031` congela el estado previo a esta línea.
- La base `fix-0.21.3` (`60e1288c1`) queda documentada como base de la investigación de fuentes (`docs/SOURCE_MANAGEMENT_RESEARCH.md`, del 29 oct).

### Fase 2 — Migración WebGPU y extensiones propias (1 nov)

- `8d5fa207d` (squash de las fases 1–3b): **fundación `androidx.webgpu`** sobre la línea `feature/webgpu-clean`: `GpuImageBackend`, puente `ca.mpreg`, decodificación de páginas hacia el pipeline GPU.
- Visor `AndroidxWebGpuViewer` pagado y continuo: gestos (pinch/pan/doble tap), caché de precarga, progreso y errores con reintento, fallback automático al visor stock, preferencia detrás de un toggle OFF por defecto (`7ae2f8f70`).
- Dos hotfixes documentados en `docs/WEBGPU_MIGRATION_PROGRESS.md`: el probe cargaba JNI sin cargar la `.so` (`8b00e5b08`, con test de regresión) y las escrituras de cola ignoraban `position/limit` (pantalla negra; `87ee0d303` confina cada write a su tamaño real). Techo de textura a 16384 para páginas webtoon altas (`bd0f4b426`).
- **Extensiones embebidas**: módulo MHScans (`8fc4f4182`) y Emperor Scan v1.6.75→76 (`218491305`, `41c052031`), con firma en CI para generar APK instalables (`37a8e6b30`).
- Reparador: «Trust all pending» (`74a2db0ad`), agrupado por fuente (`4c119f582`), **migración híbrida exacta/aproximada con umbral ajustable** (`8c8f1466c`) y cabecera From→To con confianza (`8433ad386`).

### Fase 3 — Estabilización de visor y reparador (2–4 nov)

- Reparador: bugs corregidos (`d7ee28876`), telemetría OFF en ambas CI (`750b7e231`, `b4a26187f`), conciliación de ramas (`1d0893c9a`).
- Visor: zonas de toque normalizadas al viewport real (`729d6253d` → `aa86a4390`), ajustes stock en el diálogo (`57ce07d92`), tira 3/4 de pantalla y arrastre invertido como el stock (`1ce0d9c4a`, `be845174e`), grises/invertidos por uniform (`1ac9f25f0`), espaciado lateral webtoon (`d1a939f95`), escala Min width (`7c3dff0cb`).
- Migración: kanji/hangul/cirílico en `TitleMatcher` (`702f10f68`), aproximadas a revisión manual (`65a8c653c`), aviso de fuente destino no instalada (`d64f550f1`), fallos de red ≠ sin coincidencia (`bf779adb5`), resultado único al 100% no se disfraza (`e370cb0c3`), migración masiva visible en fallos (`08004794c`), aviso de menos capítulos (`c4ef0a74b`), masiva preselecciona solo exactas (`1a39b744d`).
- Biblioteca/fuentes: **informe de fuentes difíciles al terminar la actualización** (`1366d05b6`/`7718f3e0b`), habilitación/deshabilitación en lote (`350a7dc44`).
- Lector: tira pegada entre capítulos (`b681b70fe`), página visible primero y preloads en goteo (`dd8d4d830`).
- Primer feedback del teléfono real (POCO F6, Android 16): `51d158c7e`.

### Fase 4 — Calor, firma estable y CI solo-app (5–7 nov)

- CI compila **solo la app** en cada push; extensiones solo si cambia su código, detectado vía API de compare (`73af79d78`, `8ffcbb968`).
- Lector: fondo del lector detrás de las páginas en vez de negro (`4d1a9afa1`) y **frame pacing** contra el calor (`508f0c469`).
- **Firma estable en CI** (`ef0ece3`): a partir de aquí las builds se instalan encima sin desinstalar. Intervalo de actualización de biblioteca configurable a 6 h (`5485a46f9`).
- Visor HQ `ca.mpreg:webgpuviewer` 54→58 (`36b990462`) y reanudación correcta del visor continuo (`a5237598b`).
- **Barras negras resueltas** (`92a71bd0e`): la causa era el Gap del modo tira (default de fábrica 10%); usuario lo **confirmó** el 07/11 22:21.

### Fase 5 — Sesión paralela, divergencia y reconciliación (10–12 nov)

- Auditorías upstream (10/11): sin release nueva, PR #4226 portable a demanda, issue #4240 no aplica. Regla: **no rebasar hasta v0.21.4** (`85b1900b8`, `14ec98072`).
- 11/11: fix del **acumulador de errores** (100+50=150; ahora `setErrors` fija la lista exacta, `a9bbaa7ad`), **resolvedor secundario** del reparador (`e3ef56c95`) y **motor web de respaldo** (403/503 → WebView Chromium, niveles 1+2 con pool caliente, `489668b8c`/`85ffc0d0a`). Se crea `docs/REGISTRO.md` (`31e7edf23`).
- **Sesión paralela** (rama `fix/review-2026-11-12` → `main`): reintentos de fallos transitorios + confirmación de enlaces rotos tras racha 404/410 (`8a9d518c4`), señales secundarias autor/géneros (`888a63116`) y CI acotada con actions pineadas (`5d95ba550`). Resultado: `main` y `feature/webgpu-clean` **divergen** (feature ≈120 commits adelante, main +3) y se advierte no instalar builds de `main`.
- Saga de publicación (detallada en REGISTRO 00:50–02:48): PR imposible con token fine-grained sin permisos (403 ×3); merge de reconciliación `main` → feature resolviendo 5 conflictos conservando la línea probada (`eae7fe6a2`); incidente de reciclaje del sandbox sin pérdida real; el token clásico `repo`+`workflow` del usuario desbloquea el push (02:48) → lote publicado (`d324ddbf6`), **`main` vuelve a fast-forward** y ambas ramas quedan idénticas.
- Mecanismos nuevos: **pasada en calma anti-errores de ráfaga** en actualización masiva (reintento transitorio, pacing, corte de cola, presupuesto por fuente; solo lo que falla en ambas pasadas queda en el log; `0cfd8b194`), propagación de cancelación (`13d6ba998`) y **títulos migrados dejan de aparecer** en reparador/errores (`3ba9da5cc`, verificado forensemente como primero de su caso, `e71adc7e2`).
- Cierre (03:16): la CI upstream «Build & Test» destapó violaciones de formato → `f6eb34c89` (ktlint puro) → **doble CI verde** sobre `f6eb34c8`, `main` FF a `986aa6ee4`, CHANGELOG retitulado. Build final entregada y **pendiente de confirmación del usuario**.

## Mapa de documentos

| Documento | Qué contiene |
|---|---|
| `docs/HISTORIA.md` | Este documento — narrativa consolidada y mapa del repo |
| `docs/REGISTRO.md` | **Diario operativo** desde el 06/11: fecha/hora UTC, motivo, build y estado de cada cambio. Es el documento vivo: todo cambio nuevo se anota ahí |
| `docs/TRASPASO.md` | Traspaso técnico extenso (04/11): estado verificado por archivo, regla de oro «verificado = CI verde o probado en el teléfono» |
| `CHANGELOG.md` | Changelog formal por build (secciones «Mihon Fix …» arriba del histórico upstream) |
| `docs/CAMBIOS_FORK.md` | Manifiesto puntual del 31 oct: 48 archivos cambiados vs base, mapa por funcionalidad |
| `docs/SOURCE_MANAGEMENT_RESEARCH.md` | Auditoría del 29 oct: referencias TachiyomiSY (`c1eb1e3cb`) y APIs de Mihon usadas para la gestión de fuentes |
| `docs/WEBGPU_MIGRATION_PROGRESS.md` | Notas de sesión WebGPU (01–02 nov) con los dos hotfixes profundos (probe/buffers) |

## Mapa de ramas (antes → después de la consolidación del 12/11)

Al consolidar quedaron **solo `main` y `feature/webgpu-clean`** (idénticas, `986aa6ee4`; convención vigente: trabajar en feature y fast-forward de `main`, o directamente en `main` — un FF siempre es posible). Las 6 ramas obsoletas se etiquetaron antes de eliminarlas; **restaurar cualquiera**: `git checkout -b <rama> <tag>`.

| Rama eliminada | Punta archivada | Estado al eliminar |
|---|---|---|
| `feature/source-management` | `archive/feature-source-management` (`b48144849`) | ahead 56 / behind 211 — contenido portado a `main` vía `integration` |
| `feature/webgpu-androidx` | `archive/feature-webgpu-androidx` (`fff4710e5`) | ahead 6 / behind 144 — fundación portada como squash `8d5fa207d` |
| `fix/build-green` | `archive/fix-build-green` (`f7ef6e336`) | ahead 51 / behind 211 — línea temprana absorbida por webgpu-clean |
| `fix-0.21.3` | `archive/fix-0.21.3` (`60e1288c1`) | ahead 51 / behind 211 — línea paralela temprana (base del research de fuentes) |
| `integration` | `archive/integration` (`7fb14b591`) | ahead 0 — completamente fusionada |
| `fix/repair-links-main` | `archive/fix-repair-links-main` (`b1d69aeb6`) | ahead 0 — completamente fusionada |

## Convenciones vigentes

1. **Fechas y horas en UTC** (las de git y la CI son la fuente de la verdad).
2. **Build** = `MihonFix-0.21.3-build-<sha>-arm64.apk`, nombrada por el commit de código. Firma estable: se instala encima, sin desinstalar.
3. **Todo cambio se anota en `docs/REGISTRO.md`** con fecha/hora, motivo, build y estado (`pendiente confirmar` → `confirmado` → `superado por <build>`; `sin queja`; `sin build`).
4. **Compilar solo en GitHub** (no builds locales; `b9e7c1829`).
5. **No rebasar contra upstream hasta v0.21.4** (refactor del lector #4191–4193 en vuelo). PR #4226 portable a demanda.
6. Para publicar se necesita token con **Contents: write + Workflows: write** (clásico: `repo`+`workflow`). El fine-grained de solo lectura falló 3 veces — historial en REGISTRO 00:50–02:48.

## Pendientes abiertos al cierre de esta consolidación

- **Confirmaciones de usuario pendientes** (antigüedad primero): reanudación del visor continuo (`a523759`) · acumulador de errores (`a9bbaa7`) · resolvedor secundario (`e3ef56c`) · motor web de respaldo (`85ffc0d0`) · reintentos/enlaces rotos y reparador sincronizado (`d650a1ad3`) · pasada en calma + migrados limpios (`d324ddbf` → build final `f6eb34c8`) · migración masiva opt-in de no exactas (`519c7b43`).
- **Upstream 20 commits adelante** — revisar al llegar v0.21.4.
- El changelog/REGISTRO son la memoria viva: cualquier cambio nuevo empieza por una entrada ahí.

## Cómo retomar el trabajo (para cualquier agente o persona futura)

1. Lee este documento → `docs/REGISTRO.md` (desde arriba) → `docs/TRASPASO.md` para detalles técnicos.
2. Estado verdadero del código: `main` == `feature/webgpu-clean` == `986aa6ee4`; doble CI verde en `f6eb34c8`.
3. Trabaja en `feature/webgpu-clean` o directamente en `main` (idénticas); anota en REGISTRO; fast-forward de `main` al publicar.
4. No instales builds de ramas archivadas (`archive/*`) ni uses `fix-0.21.3` como base — está superado.
