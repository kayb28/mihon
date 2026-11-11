# Registro de cambios — MihonFix (fork kayb28)

Diario operativo de la rama `feature/webgpu-clean`: **todo** cambio queda anotado aquí, por pequeño que sea, con fecha y hora, motivo, build en la que se aplicó y si queda pendiente confirmar que el arreglo tuvo efecto.

**Convenciones**

- Fechas y horas en **UTC** — las que guardan git y GitHub CI, que son la fuente de la verdad. (Algunos títulos de commits docs antiguos llevan fechas de ciclo desalineadas; aquí manda GitHub.)
- **Build** = `MihonFix-0.21.3-build-<sha>-arm64.apk`, nombrada por el commit de código que la produjo. Desde `ef0ece3` se instala encima de la anterior sin desinstalar (firma estable).
- **Estado** de cada entrada: `pendiente confirmar` → `confirmado` (se anota cuándo lo confirmó el usuario) → `superado por <build>`. `sin queja` = en uso sin reporte de fallo. `sin build` = cambio de docs/CI que no produce APK nuevo.
- El detalle técnico extenso vive en `docs/TRASPASO.md`; el changelog formal por build, en `CHANGELOG.md`. Historial anterior al 06/11: `git log`.

---

## 2026-11-11

- **14:35 — fix del acumulador de errores de actualización.** Motivo: el filtro de «títulos con problemas de actualización» y el reparador de enlaces mostraban la suma histórica (100+50=150) porque `addErrors` hacía merge y nunca borraba errores viejos. Ahora la pasada completa fija la lista exacta del último ciclo (`setErrors`) y la parcial sustituye solo lo procesado (`replaceForManga`) (`a9bbaa7ad`). **Build `a9bbaa7`** (CI 43040012633 verde) · Estado: **pendiente confirmar** — instalada por el usuario, aún sin probar.
- **18:41 — resolvedor secundario del reparador de enlaces.** Motivo: si la fuente del título ya no da rastro de él (fuente caída o extensión desinstalada), el reparador busca el título exacto en las demás fuentes instaladas y lo ofrece como «Coincidencia exacta en <fuente>», confirmado una por una porque migrar cambia la disponibilidad de capítulos (`e3ef56c95`). **Build `e3ef56c`** (CI 43080168520 verde) · Estado: **pendiente confirmar**.
- **19:41 — primer intento del motor web de respaldo: CI roja por un import faltante (`toWebViewHeaders`) (`489668b8c`).** Estado: superado por el reenvío de las 19:46.
- **19:46 — motor web de respaldo (niveles 1+2).** Motivo: fuentes que fallan en Mihon pero abren bien en el navegador (Cloudflare ata `cf_clearance` a la huella TLS del navegador que resolvió el reto; el reintento de OkHttp no es un navegador). Nivel 1: tras un 403/503, `WebViewFetchFallbackInterceptor` re-emite la petición con `fetch()` dentro de un WebView headless de Chromium y reconstruye la respuesta como OkHttp normal. Nivel 2: si un host lo necesita ≥2 veces en 10 min, su WebView queda caliente en un pool (TTL 2 min, máx 2, barrido cada 30 s). Interruptor en Ajustes → Avanzado → Red, activado por defecto; los fallos no rompen nada (respuesta original + cooldown 10 min) (`85ffc0d0a`). **Build `85ffc0d0`** (CI 43090297988 verde, APK entregado 20:04) · Estado: **pendiente confirmar** — recomendada al usuario como siguiente instalación.
- **20:25 — corrección de fechas en `CHANGELOG.md`.** Motivo: las entradas de las builds `a9bbaa7` (decía 09/11), `92a71bd` y `a523759` (decían 07/11) llevaban las fechas de los títulos de ciclo, desalineadas; puestas a las reales de GitHub CI. `sin build` (solo docs) · Estado: —
- **20:25 — creación de este registro.** Motivo: petición del usuario de dejar constancia en el fork de cada cambio, por pequeño que sea, con fecha/hora, motivo, build y estado de confirmación. `sin build` (solo docs) · Estado: —

## 2026-11-10

- **11:30 — auditorías upstream (docs).** Sin release nueva (v0.21.3 sigue siendo la última); PR #4226 (crop-instead-of-zoom) portable a demanda; issue #4240 (e-ink ripple) no aplica. Regla fijada: **no rebasar hasta v0.21.4** (refactor del lector #4191–4193 en vuelo). `sin build` · Estado: —

## 2026-11-06

- **06:41 — firma estable en CI + intervalo de actualización de biblioteca a 6 h.** Motivo: poder instalar builds nuevas encima sin desinstalar (antes cada runner firmaba con su keystore debug aleatorio) y que la biblioteca consulte fuentes más a menudo. Commits `5485a46` (intervalo) y `ef0ece3` (firma); extensiones MHScans y EmperorScan re-firmadas. **Build `ef0ece3`** (CI verde, APK entregado 06:56) · Estado: `sin queja` — base de uso del 06/11 al 11/11. Nota: esta build exigió una última desinstalación; todas las siguientes se instalan encima.
- **17:52 — feedback del visor experimental en el POCO F6 (barras, calor, grises) (docs, `9f904c718`).** De aquí salió la caza de las barras negras. `sin build` · Estado: —
- **18:32 — fix de reanudación del visor continuo experimental + bump del visor WebGPU 54→58.** Motivo: reabrir un capítulo a mitad de lectura caía en la primera página en vez de la guardada (la restauración se calculaba contra un layout sin tamaño real); y el visor HQ se actualizó con pacing de tiles, reúso de pipelines y fixes de gestos (`a5237598b`, `36b990462`). **Build `a523759`** (CI 42271630287 verde) · Estado: **pendiente confirmar**.
- **21:30 — fix del gap del modo tira vertical discontinuo (barras negras).** Motivo: el ajuste Gap del propio modo traía un default de fábrica del 10 % de altura de pantalla con slider que no bajaba de 1 %; ahora default 0, slider 0..100 y renombrado a «Separación entre páginas» (`92a71bd0e`). **Build `92a71bd`** (CI verde) · Estado: **confirmado** por el usuario a las 22:21 del mismo día — barras negras eliminadas.

---

## Pendientes abiertos

- **Confirmaciones pendientes** (antigüedad primero): reanudación del visor continuo (`a523759`, 06/11) · acumulador de errores de actualización (`a9bbaa7`, 11/11) · resolvedor secundario (`e3ef56c`, 11/11) · motor web de respaldo (`85ffc0d0`, 11/11).
- **No rebasar** contra upstream hasta v0.21.4 (refactor del lector #4191–4193 en vuelo). PR #4226 portable a demanda.
