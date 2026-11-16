# Instrucciones para agentes — MihonFix (fork kayb28)

Reglas operativas que **todo** cambio en este repo debe respetar. Contexto e historia: `docs/HISTORIA.md`; detalles técnicos: `docs/TRASPASO.md`; registro obligatorio de cada cambio: `docs/REGISTRO.md`.

## Regla crítica: la app debe poder actualizarse sin desinstalar

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
- Opcional, para verificar el APK: `keytool -printcert -jarfile <apk>` debe mostrar el mismo certificado que la build anterior (CN=MihonFix).
- Todo cambio que toque firma, identidad de la app o CI de build se anota en `docs/REGISTRO.md` y se comunica al usuario.

El resto de convenciones (registro, CI, ramas, upstream) están en `docs/HISTORIA.md`, «Convenciones vigentes» y «Cómo retomar el trabajo».
