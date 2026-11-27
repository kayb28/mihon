# INFORME T-REB — Rename del repositorio + identidad visual «MihonFix»

Rama: `docs/rebrand-mihonfix` (cortada de `main` @ `bd4aa16e6`). Encargo: PROMPT 9-ADD-2.
Fecha: 2026-11-27. Agente: GLM. PR: #16.

## Entregables → commit

| Requisito | Dónde quedó | Commit |
| --- | --- | --- |
| **R0** Rename `kayb28/mihon` → `kayb28/mihonfix` vía API (PATCH /repos) | Hecho ANTES que nada (el ADD lo exige primero). Evidencia: respuesta API `full_name: kayb28/mihonfix`; curl a la URL vieja → **301** → `https://github.com/kayb28/mihonfix`; URL nueva → 200; `origin` local actualizado | (operación de repo, no commit) |
| **R1** Adaptive icon: fondo tinta `#12141A` + logo; `app_name` → «Mihon Fix»; PROHIBIDO tocar applicationId/versionName/versionCode/firma | `ic_launcher_background.xml` (todos los fill → `#12141A`), `ic_launcher_foreground.xml` (glifo → ámbar `#E8B54A` para legibilidad sobre tinta; MISMOS trazados, sin rediseño), `app_name` en i18n base (translatable=false) | `de705a950` |
| **R2** README: banner (icono + «MIHON FIX», gruesa, ámbar sobre tinta) + título del fork; atribución Mihon/AGPL; README de upstream conservado debajo | `README.md` | `de705a950` |
| **R3** REGISTRO + CHANGELOG («Other: rename repo → kayb28/mihonfix + identidad visual MihonFix»); sustituir referencias a `kayb28/mihon` en AGENTS/HISTORIA/REGISTRO; sin «pendiente de rename» | `docs/REGISTRO.md`, `CHANGELOG.md`, `README.md` (3), `docs/REGISTRO.md` (7), `docs/HISTORIA.md` (título), `AGENTS.md` (cabecera) | `de705a950` |

## Prohibiciones respetadas

- `applicationId` (`app.mihonfix`), `versionName`/`versionCode` (40), firma: **INTACTOS**
  (verificable con `scripts/verify_apk_signature.py` sobre cualquier build de la rama).
- El logo NO se rediseñó: los trazados vectoriales existentes se conservaron; solo cambiaron
  los colores de fondo/glifo a los especificados en el encargo.

## Pendiente del usuario (registrado en REGISTRO)

El ADD-2 menciona «logo SVG aportado por el usuario»: **no llegó a `upload/`** (el
directorio solo contiene los prompts y el token). Registrado como pendiente: cuando el
usuario aporte el SVG, se sustituye el foreground/monochrome por su SVG tal cual (sin
rediseñar).

## CI

Run 45953288088 sobre `de705a950` (punta): **success** — job «Build & Test App» verde, con
lo cual el icono/nuevos recursos compilan y todos los tests pasan.

## Checklist de dispositivo (usuario)

1. Icono nuevo (tinta + glifo ámbar) y nombre «Mihon Fix» en el launcher.
2. Instalación encima de la build previa SIN desinstalar.
3. Firma intacta: `python scripts/verify_apk_signature.py <apk>` (vía argv).

## En criollo

El repo ahora se llama `kayb28/mihonfix` (los links viejos redirigen solos, nada se rompe),
y la app se muestra como «Mihon Fix» con el icono oscuro y el glifo en ámbar. Todo lo demás
— paquete, versión, firma — quedó exactamente igual, así que se instala encima sin
desinstalar. Falta el logo definitivo del usuario cuando lo suba.

## 📲 BUILD PARA INSTALAR

Build de la punta de la rama `docs/rebrand-mihonfix` (`de705a950`, run 45953288088, job
«Build & Test App» verde; el APK del artefacto `INSTALAR-de705a950…` del workflow
«Build APK», nombre interno `MihonFix-0.21.3-build-<timestamp>-arm64.apk`, firma ESTABLE):

- **Artefacto:** `INSTALAR-<sha>` (job «Build Release APK») de la rama `docs/rebrand-mihonfix`.
- **Verificación de firma:** `python scripts/verify_apk_signature.py <apk-descargado>`
  (ruta del APK pasada por argv, como exige AGENTS.md §5.4).
- sha256 y tamaño exactos del APK: descargar el artefacto y computarlos — el encargo exige
  listarlos, y con el token actual la descarga del artefacto no se completó en esta sesión
  (quedó pendiente; el REGISTRO lo anota). **No se inventan valores.**

**Nota honesta:** el run de CI de esta rama es del workflow de tests (verde); el artefacto
INSTALAR lo produce el workflow «Build APK» en cada push — el usuario lo descarga de la
pestaña Actions de la rama con el nombre de arriba.
