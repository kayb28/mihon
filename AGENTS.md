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
- Para verificar el APK (los APKs modernos firman con v2/v3, NO con firma JAR — `keytool -printcert -jarfile` responde «Not a signed jar file»): `python3 scripts/verify_apk_signature.py <apk>` debe responder «ESTABLE». El certificado estable del fork es SHA-256 `DE7889F34D731EF961794F0837B78B86D025387F20E036F357C94742D1296AD0` (CN=MihonFix, válido desde el 06/11/2026).
- Todo cambio que toque firma, identidad de la app o CI de build se anota en `docs/REGISTRO.md` y se comunica al usuario.

### Si se reporta que una build «no se instala encima»

Descartar la firma con evidencia antes de asumirla. Forense del 16/11: **todas** las builds de la CI del fork desde `ef0ece3` (ramas `main`, `feature/webgpu-clean`, PRs incluidos) comparten el certificado estable — un rechazo con builds recientes casi nunca es de firma. Checklist en orden:

1. **Firma**: descargar el artefacto de CI del run en cuestión y correr `python3 scripts/verify_apk_signature.py <apk>` → debe dar ESTABLE. Comparar también con la build que el usuario tiene instalada si se puede.
2. **versionCode**: el del APK nuevo debe ser ≥ al instalado (`versionCode` en `app/build.gradle.kts`).
3. **applicationId**: debe ser `app.mihonfix` sin sufijo (los APKs con sufijo `.dev`/`.foss`/`.debug`/`.benchmark` son apps distintas para Android y exigen desinstalar).
4. Si firma/id/versionCode coinciden, la causa está en el **archivo o en el teléfono**: (a) archivo equivocado — instalar el `.zip` del artefacto en vez del APK de dentro, ABI incorrecta (el POCO F6 es arm64-v8a) o descarga truncada (verificar el sha256 del APK entregado); (b) **Play Protect** bloqueando → «Más detalles → Instalar de todos modos»; (c) instalador MIUI/HyperOS → activar «Instalación vía USB» / desactivar «Optimización MIUI» en Opciones de desarrollador, o instalar aceptando el aviso de seguridad.
5. Si al final hay que desinstalar una vez: antes, copia de seguridad de Mihon (Ajustes → Copias de seguridad → Crear y exportar) para restaurar biblioteca, historial y ajustes tras reinstalar. El almacén de errores del reparador NO va en la copia: se reconstruye solo.

El resto de convenciones (registro, CI, ramas, upstream) están en `docs/HISTORIA.md`, «Convenciones vigentes» y «Cómo retomar el trabajo».
