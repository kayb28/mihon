# AGENTS.md — guía para agentes en este repositorio

_Fork de Mihon («MihonFix»). Última actualización de esta guía: 2026-11-26._
_La fuente de la verdad para estado y CI es GitHub (convenciones en `docs/REGISTRO.md`)._

## 1. Qué es este proyecto

- Fork operativo del lector de manga **Mihon** (Android, Kotlin, Gradle, Jetpack Compose),
  mantenido como **MihonFix** (paquete `app.mihonfix`, firma estable `CN=MihonFix`).
- Incluye en el mismo repo dos extensiones propias con build en CI:
  `extension-emperorscan/` y `extension-mhscans/`.
- Estructura principal: `app/` (UI), `core/` (red, common…), `data/`, `domain/`,
  `presentation-*`, `i18n/`, `docs/`.
- Documentación interna (leer antes de tocar nada):
  - `docs/REGISTRO.md` — diario operativo: todo cambio con fecha/hora UTC, motivo,
    build y estado (pendiente confirmar → confirmado → superado).
  - `docs/TRASPASO.md` — detalle técnico extenso.
  - `docs/HISTORIA.md` — narrativa consolidada y mapa de documentos/ramas.
  - `CHANGELOG.md` — changelog formal por build.
  - `README.md` — banner de fork al inicio; el resto es el README de upstream.

## 2. Cómo compilar y probar

- Requisitos: Android SDK + JDK (versión fijada por `actions/setup-java` en
  `.github/workflows/`); build con el wrapper `./gradlew`. CI usa Gradle 9.8.2.
- Comandos relevantes (los que corre CI):
  - `./gradlew :core:common:compileReleaseKotlin` — compila el módulo del fallback WebView.
  - `./gradlew assembleDebug` / `assembleRelease` — APK (CI entrega arm64 como
    `MihonFix-0.21.3-build-<timestamp>-arm64.apk`, donde `<timestamp>` = `<sha>-<yyyymmdd-hhmm UTC>`).
  - `./gradlew :core:common:testDebugUnitTest` — tests JVM de `core:common`.
- CI (GitHub Actions), dos workflows (verificados el 2026-11-26):
  - **Build APK** (`.github/workflows/build_apk.yml`) — en CADA push a cualquier rama corre
    primero el job **«Spotless & Unit Tests»** (`spotlessCheck` + `testDebugUnitTest` con
    reporte de tests; solo un disparo manual `workflow_dispatch` con `skip_tests: true`
    puede saltárselo), y después los jobs de build: Build Release APK, Build Unminified
    APK (No R8), builds de las dos extensiones, Detect extension changes.
  - **Build & Test** (`.github/workflows/build.yml`) — en PRs y en cada push a `main`:
    Dependency Review (solo en PRs; requiere Dependency graph activado en settings del
    repo), `spotlessCheck`, `testDebugUnitTest` y **`verifySqlDelightMigration`** (este
    workflow es el único que verifica las migraciones de SQLDelight).
- ⚠️ El sandbox del agente NO tiene Android SDK ni Gradle: `./gradlew` no funciona aquí.
  Para probar el protocolo de sesión a nivel JVM hay un harness FUERA del repo:
  `bash /home/z/my-project/scripts/run_fetchsession_tests.sh` (kotlinc 2.4.21 + JUnit
  console; compila `FetchSession.kt` y `FetchSessionProtocolTest.kt`; 17/17 al 2026-11-15).
  IMPORTANTE: el harness NO compila `WebViewFetchFallbackInterceptor.kt` ni ningún otro
  archivo del repo — un commit no queda probado solo porque el harness pase; CI decide.

## 3. Ramas

(Estado verificado contra GitHub el 2026-11-27, tras el merge del PR #14.)

- `main` — rama de distribución. Punta: `bd4aa16e` (merge PR #14: P7 motor web
  secundario, W1–W5; antes `7d10926`, merge PR #13 de T6 — Ronda 2 validada y puerta de
  zoom aprobada por el usuario 26/11; antes PRs #8–#12: docs post-auditoría, tests B1–B4,
  T1–T7 del visor, guards de migración). La reconciliación con `feature/webgpu-clean`
  está resuelta desde la Release `v0.21.3-fork.1` (2026-11-19). Las builds de `main` se
  instalan encima (firma estable, versionCode 40).
- `feat/p8-domain-override` — PROMPT 8/9 T-P8: regla por fuente «Editar dominio»
  (from_host→to_host) compartida por OkHttp y el fallback WebView de P7. En curso
  (D1–D6 con tests JVM); PR pendiente; merge solo con orden explícita del usuario.
- `docs/consolidacion-2026-11-27` — PROMPT 9 T-D1: consolidación documental
  (cierre Fallo C, apertura Fallo E, ADOPCIONES_UPSTREAM, decisiones V1–V8/1b, prefijo
  NO-INSTALAR- en artefactos de `build.yml` — V3). PR pendiente.
- `fix/fallo-e-textura` — PROMPT 9 T-E: Fallo E (textura 800×50000 > límite GPU en el
  visor EXPERIMENTAL WebGPU; troceado en teselas). En curso.
- `feat/p5-reintentar-titulos-error` — PROMPT 9 T-B: botón «Reintentar títulos con
  error» en UpdateErrorsScreen. En curso.
- `feature/webgpu-clean` — línea del ciclo anterior; punta `736fa6baa` (= `v0.21.3-fork.1`),
  consolidada en `main`. Archivo histórico; no tocar.
- `feat/t6-page-state-machine-wiring` — MERGEADA en `main` (PR #13, `7d10926`).
  Su predecesora `feat/reader-page-state-wiring` queda como archivo histórico.
- `fix/migration-guard-fixes` — MERGEADA (`152027f`).
- `docs/coherencia-post-auditoria-reparador` — PR #8, MERGEADA (`b5a1323`).
- `fix/reparador-enlaces-tests` — PR #9, MERGEADA (`00f465d`).
- `fix/viewer-refactor-t1-t7` — T1–T7, squash `c8d2f52` en `main`.
- `ci/branch-checks` — job «Spotless & Unit Tests» por push (C6/C7), mergeada `ca86c55`.

## 4. Qué está verificado y qué no (corte: 2026-11-26)

Verificado:
- PR #1 **mergeeado en `main`** el 2026-11-17 18:16 UTC, incluido el fix de compilación
  `UNSAFE_CALL` (`2222d6ca057494b49f183f3d9ddabe283b84737d`, «cuerpo nulo de la sesión
  del fallback WebView tratado como fallo controlado»). Rutas en `main`:
  `core/common/src/main/kotlin/eu/kanade/tachiyomi/network/interceptor/WebViewFetchFallbackInterceptor.kt`,
  `…/FetchSession.kt` y `core/common/src/test/…/FetchSessionProtocolTest.kt` (17 tests,
  corren en CI vía `testDebugUnitTest`).
- Release `v0.21.3-fork.1` publicada (2026-11-19 03:13 UTC) con APK arm64 firmado con
  el cert estable; CI verde en `18dc37a` (runs 44449995174 / 44449995252).
- `main`: CI doble verde en su punta `152027f` (runs 45542702890 / 45542703056,
  2026-11-25 03:50 UTC).

No verificado o pendiente:
- ⚠️ El harness JVM local (FUERA del repo, `bash /home/z/my-project/scripts/run_fetchsession_tests.sh`)
  compila `FetchSession.kt` + `FetchSessionProtocolTest.kt` pero **NO compila
  `WebViewFetchFallbackInterceptor.kt`** ni ningún otro archivo del repo — un commit no
  queda probado solo porque el harness pase; CI decide.
- ⚠️ Las rutas reales de WebView (mundo de página, POST binario, puente legacy) siguen
  **sin validar en dispositivo**: verificadas solo por inspección, harness JS local y
  tests JVM del protocolo de sesión. «Confirmado» solo cuando el usuario lo valide en
  su teléfono.
- Cableado del lector T6a + fixes R1–R4 (`feat/t6-page-state-machine-wiring`): CI verde
  y 205 tests, pero **pendiente de validación en dispositivo** (checklist Ronda 2).

## 5. Reglas obligatorias

1. **Un cambio por commit**: cada commit aborda exactamente un cambio (un fix, un doc,
   un test). No mezclar.
2. **Nada directo a `main`**: todo cambio entra por PR con CI. Sin push a `main`.
3. **Actualizar `docs/REGISTRO.md` antes de terminar cualquier tarea**: añadir entrada
   con fecha/hora UTC, motivo, qué se hizo, commit/build que lo produjo y estado, según
   las convenciones que el propio REGISTRO.md define en su cabecera.
   La entrada de `docs/REGISTRO.md` puede ir en un commit propio dentro de la misma rama:
   así el commit de código y el de registro siguen siendo un cambio cada uno.
4. **Qué build instalar** (regla del usuario, 2026-11-26): el entregable de TODO encargo
   termina con un bloque 📲 «BUILD PARA INSTALAR» — o su versión «Ninguna — encargo sin
   build nueva». Si el informe no marca build, no se instala nada. La build instalable es
   el APK arm64 del artefacto `INSTALAR-<sha>` del job «Build Release APK» (desde
   `ci/nomenclatura-builds` se llama `MihonFix-0.21.3-build-<sha>-<yyyymmdd-hhmm UTC>-arm64.apk`).
   Todo artefacto con prefijo `NO-INSTALAR-` (No-R8 `-nor8`, tests `-test`) es aid de
   depuración: no se instala. Los nombres históricos citados en docs antiguos siguen
   siendo válidos como historia.

## 6. Operativa del entorno del agente

- `gh` no está instalado: push con token y PR/checks vía API REST (scripts de
  referencia en el workspace: `create_pr.py`, `verify_pr.py`, `ci_diag.py`).
- El token va en la variable `GIT_TOKEN` del proceso: nunca en disco, ni en
  `.git/config`, ni en logs; el usuario lo rota tras cada uso.
- Los logs de CI se descargan en dos pasos: la API responde 302 hacia el blob firmado;
  el `Authorization` NO debe reenviarse a ese host.
- El workspace del agente vive FUERA del repo (`/home/z/my-project/`): `scripts/`
  (harness y automatización), `download/` (entregables), `upload/` (entradas del
  usuario). Nada de eso se commitea al repo.

## 7. Regla crítica: la app debe poder actualizarse sin desinstalar

Desde `ef0ece3` (06/11/2026) cada build se instala encima de la anterior en el teléfono del usuario **sin desinstalar**. Ningún cambio aquí puede romper eso. Depende de tres invariantes:

### 1. Firma estable (no tocar)

- Los APKs `release` se firman con el keystore que la CI toma de los secretos de GitHub `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` y `KEY_PASSWORD`: el paso «Setup Signing Keystore» de `.github/workflows/build_apk.yml` los convierte en `MIHON_GITHUB_RELEASE`, `storeFile`, `storePassword`, `keyAlias` y `keyPassword`, que consume `app/build.gradle.kts`.
- **Prohibido**: quitar o alterar ese paso de CI, el bloque `signingConfigs` / `buildTypes.release.signingConfig` de `app/build.gradle.kts`, o cambiar de keystore. Un keystore distinto hace que Android obligue a desinstalar la app para instalar la nueva (con pérdida de biblioteca, progreso y ajustes).
- Si un log de CI dice «No custom keystore found in secrets. Falling back to debug signing.», **esa build NO se entrega al usuario**: cada runner firmaría con su keystore debug aleatorio y efímero. Restaurar los secretos y recompilar.

### 2. applicationId fijo (`app.mihonfix`)

- El id de la app en release es `app.mihonfix` (`app/build.gradle.kts`). No renombrarlo ni alterarlo: la biblioteca, el progreso de lectura y los ajustes del usuario viven bajo ese id, y un cambio de id es una app «nueva» que exige desinstalar la vieja.
- Los sufijos por buildType (`.dev`, `.foss`, `.debug`, `.benchmark`) existen para builds paralelas de desarrollo; no cambiarlos.

### 3. versionCode nunca baja

- `versionCode` (hoy 39) debe ser siempre **≥** al de cualquier build que el usuario tenga instalada. Al subir `versionName`, no reducir `versionCode`.

### Verificación antes de entregar una build

- Los logs del job «Build Release APK» deben contener «Keystore secret found. Configuring release signing.»
- Para verificar el APK (los APKs modernos firman con v2/v3, NO con firma JAR — `keytool -printcert -jarfile` responde «Not a signed jar file»): `python3 scripts/verify_apk_signature.py <apk>` debe responder «ESTABLE». El certificado estable del fork es SHA-256 `DE7889F34D731EF961794F0837B78B86D025387F20E036F357C94742D1296AD0` (CN=MihonFix, válido desde el 06/11/2026).
- Todo cambio que toque firma, identidad de la app o CI de build se anota en `docs/REGISTRO.md` y se comunica al usuario.

### Si se reporta que una build «no se instala encima»

Descartar la firma con evidencia antes de asumirla. Forense del 16/11: **todas** las builds de la CI del fork desde `ef0ece3` (ramas `main`, `feature/webgpu-clean`, PRs incluidos) comparten el certificado estable — un rechazo con builds recientes casi nunca es de firma. Checklist en orden:

1. **Firma**: descargar el artefacto de CI del run en cuestión y correr `python3 scripts/verify_apk_signature.py <apk>` → debe dar ESTABLE. Comparar también con la build que el usuario tiene instalada si se puede.
2. **versionCode**: el del APK nuevo debe ser ≥ al instalado (`versionCode` en `app/build.gradle.kts`).
3. **applicationId**: debe ser `app.mihonfix` sin sufijo (los APKs con sufijo `.dev`/`.foss`/`.debug`/`.benchmark` son apps distintas para Android y exigen desinstalar).
4. Si firma/id/versionCode coinciden, la causa está en el **archivo o en el teléfono**: (a) archivo equivocado — instalar el `.zip` del artefacto en vez del APK de dentro, ABI incorrecta (el POCO F6 es arm64-v8a) o descarga truncada (verificar el sha256 del APK entregado); (b) **Play Protect** bloqueando → «Más detalles → Instalar de todos modos»; (c) instalador MIUI/HyperOS → activar «Instalación vía USB» / desactivar «Optimización MIUI» en Opciones de desarrollador, o instalar aceptando el aviso de seguridad.
5. Si al final hay que desinstalar una vez: antes, copia de seguridad de Mihon (Ajustes → Copias de seguridad → Crear y exportar) para restaurar biblioteca, historial y ajustes tras reinstalar. El almacén de errores del reparador NO va en la copia: se reconstruye solo.
