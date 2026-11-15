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

- `main` — **rama oficial**. Punta al corte: `110c8f76e`. Todo PR parte de `main`.
- `fix/webview-fetch-pr1` — PR #1 (fallback WebView: puente en mundo de página, POST
  binario, puente endurecido). Punta `b24f4e29f`, 4 commits, en revisión, CI roja (§4).
  No rebasar ni pushear encima sin autorización explícita.
- Ramas nuevas: prefijos `fix/` (código) o `docs/` (documentación), cortadas desde `main`.
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
