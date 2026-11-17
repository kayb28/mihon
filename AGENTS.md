# AGENTS.md — guía para agentes en este repositorio

_Fork de Mihon («MihonFix»). Última actualización de esta guía: 2026-11-15._
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
    `MihonFix-0.21.3-build-<sha>-arm64.apk`).
  - `./gradlew :core:common:testDebugUnitTest` — tests JVM de `core:common`.
- CI (GitHub Actions), dos workflows:
  - **Build APK** — Build Release APK, Build Unminified APK (No R8), builds de las dos
    extensiones, Detect extension changes. No revisa formato.
  - **Build & Test** — Dependency Review (requiere Dependency graph activado en settings
    del repo) y `spotlessKotlinCheck` en `core/common`.
- ⚠️ El sandbox del agente NO tiene Android SDK ni Gradle: `./gradlew` no funciona aquí.
  Para probar el protocolo de sesión a nivel JVM hay un harness FUERA del repo:
  `bash /home/z/my-project/scripts/run_fetchsession_tests.sh` (kotlinc 2.4.21 + JUnit
  console; compila `FetchSession.kt` y `FetchSessionProtocolTest.kt`; 17/17 al 2026-11-15).
  IMPORTANTE: el harness NO compila `WebViewFetchFallbackInterceptor.kt` ni ningún otro
  archivo del repo — un commit no queda probado solo porque el harness pase; CI decide.

## 3. Ramas

- `feature/webgpu-clean` — **línea de trabajo WebGPU**: visor, motor web de respaldo,
  migración y el resto de funciones del fork. Punta al corte: `4f4b79c46`. Verificado:
  la rama existe y apunta a ese commit. Es donde se desarrolla el código nuevo, según
  `docs/TRASPASO.md` y `docs/REGISTRO.md`.
- `main` — rama de corte de upstream y distribución. Punta: `110c8f76e` (commit de solo
  docs). **La relación exacta entre `main` y `feature/webgpu-clean` está por verificar.**
  Antes de abrir cualquier PR, ejecuta `git rev-list --left-right --count main...feature/webgpu-clean`
  y deja el resultado en la descripción del PR.
- ⚠️ No instales ninguna build generada desde `main` mientras no esté reconciliada con
  `feature/webgpu-clean` (advertencia de `docs/REGISTRO.md`).
- `fix/webview-fetch-pr1` — PR #1 (fallback WebView: puente en mundo de página, POST
  binario, puente endurecido). Punta `b24f4e29f`, 4 commits, en revisión, CI roja (§4).
  No rebasar ni pushear encima sin autorización explícita.
- Ramas nuevas: prefijos `fix/` (código) o `docs/` (documentación). Córtalas desde la
  rama base que indique la tarea. Si la tarea no indica base, pregunta antes de decidir.
- Las ramas obsoletas están archivadas como `archive/*` (ver `docs/HISTORIA.md`;
  restaurar una con `git checkout -b <rama> <tag>`).

## 4. Qué está verificado y qué no (corte: 2026-11-15)

Verificado:
- `FetchSession.kt` + `FetchSessionProtocolTest.kt`: **17/17** en el harness JVM local.
- La serie de parche del PR #1 aplica limpio sobre `110c8f76e`
  (`git apply --check`, exit 0).

No verificado o fallando:
- **CI del PR #1 en rojo** (5 checks failure / 1 success). Los 4 jobs de build fallan en
  `:core:common:compileReleaseKotlin` con `Kotlin compiler: UNSAFE_CALL` —
  «Only safe (?.) or non-null asserted (!!.) calls are allowed on a nullable receiver
  of type 'ByteArray?'» en `WebViewFetchFallbackInterceptor.kt:342`. NO corregido aún.
- El check `Build & Test App` falla en el step `Dependency Review`: «Dependency review
  is not supported on this repository» — falta activar Dependency graph en settings del
  repo (configuración, no código).
- `Detect extension changes`: success.
- Rutas reales de WebView (mundo de página, POST binario, puente legacy): verificadas
  solo por inspección y harness JS local; sin dispositivo.
- La corrección del punto 342 es prerequisito para aprobar/mergear el PR #1.

## 5. Reglas obligatorias

1. **Un cambio por commit**: cada commit aborda exactamente un cambio (un fix, un doc,
   un test). No mezclar.
2. **Nada directo a `main`**: todo cambio entra por PR con CI. Sin push a `main`.
3. **Actualizar `docs/REGISTRO.md` antes de terminar cualquier tarea**: añadir entrada
   con fecha/hora UTC, motivo, qué se hizo, commit/build que lo produjo y estado, según
   las convenciones que el propio REGISTRO.md define en su cabecera.
   La entrada de `docs/REGISTRO.md` puede ir en un commit propio dentro de la misma rama:
   así el commit de código y el de registro siguen siendo un cambio cada uno.

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
