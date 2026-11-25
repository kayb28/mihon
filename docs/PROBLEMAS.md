# Problemas del fork — MihonFix (kayb28)

Registro de problemas reportados por el usuario, su estado y quién/cuándo lo confirmó.
Una fila por problema. Convenciones de fecha y hora en `docs/REGISTRO.md` (UTC).

| Problema | Estado | Detalle |
| --- | --- | --- |
| Calor del visor | Cerrado | Confirmado por el usuario: sin calor en versiones recientes (2026-11-17). |
| Fallo A — zoom en EXPERIMENTAL | Cerrado como preexistente (feature ausente) | Reportado en dispositivo (build `…da2cf43`): ni pellizco ni doble toque zoomean. Diagnóstico: solo ocurre en modos continuos (WEBTOON / vertical continuo), donde el visor experimental NUNCA tuvo zoom — su KDoc lo declara fase futura («Pinch zoom stays on the HQ viewer for this mode until a later phase»); el paginado experimental sí zoomea (capa T4). No es regresión del cableado T6a: `git diff 1520257f..c2de25f` no toca gestos. El zoom continuo queda listado como pendiente de fase futura; el zoom del paginado se revalida en Ronda 2. |
| Fallo B — drift +1 al reabrir | Cerrado con fix `d3255b6` (pendiente Ronda 2) | Reportado en dispositivo: reabrir un capítulo sin moverse avanza una página por apertura (determinista). Diagnóstico: PREEXISTENTE del visor continuo experimental — la restauración ancla la página arriba del viewport pero el reporte contaba la última página cuyo COMIENZO asoma; una página restaurada más corta que el viewport reportaba la siguiente y se guardaba como leída (+1 por ciclo). Fix: regla end-visible en política pura `ContinuousPageReportPolicy` (rojo por comportamiento `2f733e4`, verde `d3255b6`+`083edad`, drift=0 tras 5 ciclos en JVM). HQ/STOCK no se tocan. |
