# Traspaso de estado — rama `feature/webgpu-clean`

- Repositorio: `kayb28/mihon` (fork WebGPU de Mihon)
- Rama: `feature/webgpu-clean`
- Fecha del traspaso: 04/11/2026, 08:30 (hora de São Paulo)
- Regla de oro de este traspaso: solo se marca «verificado» lo que pasó por CI verde o se probó en el teléfono del usuario. Revisar código no cuenta como verificado.

## 1. Estado de la rama

- **HEAD al generar este documento:** `9758ddc` (último commit de código; el commit de este documento va encima).
- Aviso de sesión: la copia local quedó desactualizada (sin los commits `65a8c65..41a3c74` de la actualización general ya publicada); se recuperó con `git fetch` y los dos commits nuevos de este ciclo se reaplicaron con cherry-pick sobre `41a3c74`. El fix de fuente destino no instalada no se reaplicó porque el remoto ya lo traía (`d64f550`).
- La prueba del teléfono del 03/11 por la tarde usó el APK del run 41720335566 (`41a3c74`, solo documentación encima de `5f82393`), entregado por el chat, no descargado de GitHub.

Últimos commits, de más reciente a más antiguo (fechas en UTC):

| Hash | Fecha (UTC) | Mensaje |
|---|---|---|
| `9758ddc` | 2026-11-04 | fix: nombres correctos de los textos del dialogo de fallos |
| `0800479` | 2026-11-04 | fix: la migracion masiva avisa de los fallos en vez de terminar como si nada |
| `e370cb0` | 2026-11-04 | fix: el 100% de resultado unico ya no se disfraza de coincidencia de titulo |
| `41a3c74` | 2026-11-03 21:16 | docs: traspaso de estado tras la actualizacion general (5f82393) |
| `5f82393` | 2026-11-03 21:10 | fix: hacer suspend los helpers nuevos que consultan el SourceManager |
| `be5c763` | 2026-11-03 21:05 | style: ajustes de formato de spotless (locales y arrastrados) |
| `7c3dff0` | 2026-11-03 20:35 | fix: honrar la escala de tira (Min width) en la tira androidx |
| `d1a939f` | 2026-11-03 20:15 | fix: aplicar el espacio lateral webtoon en la tira androidx |
| `1ac9f25` | 2026-11-03 19:35 | fix: conectar grises e invertidos al visor androidx via uniform del shader |
| `bf779ad` | 2026-11-03 19:05 | fix: los fallos de red en la busqueda se muestran como error, no como sin coincidencia |
| `d64f550` | 2026-11-03 18:50 | fix: la migracion avisa cuando la fuente destino no esta instalada |
| `02cf7d5` | 2026-11-03 18:15 | merge: incorporar el traspaso de estado (d1a776d) al fix pendiente del lector |
| `1bdd3e9` | 2026-11-03 19:18* | fix: que el visor androidx complete la integracion con el loader como el stock |

\* `1bdd3e9` fue creado en una sesión anterior (2026-11-03 19:18 UTC) y quedó sin publicar; este ciclo lo incorporó.

### Ciclo del 04/11 (este traspaso) — motivado por la prueba de migración del usuario

El usuario probó la búsqueda de la migración con 12 mangas (abortando a propósito antes de confirmar) y reportó dos problemas del lector como resueltos. En sus capturas se vio el caso que quedaba pendiente (§6.7): «Las conspiraciones del mercenario» (LeerCapitulo, 113 caps) apareció en Kagane como «Return of the Heavenly Monarch» (21 caps) con un 100 % que no significaba nada — es la auto-aceptación de resultado único, que asigna 1.0 sin comparar títulos (el parche de `702f10f`/`65a8c65` arregló los falsos 100 % por normalización, pero no esta vía).

| Commit | Qué hace |
|---|---|
| `e370cb0` | La insignia miente menos: un candidato auto-aceptado por ser el único resultado ya se muestra como «ÚNICO RESULTADO» (ámbar) en vez de «100 %»; EXACTO y los porcentajes reales no cambian. Sin cambio de comportamiento: el candidato sigue utilizable, pero la señal visual dice la verdad |
| `0800479` | La migración masiva ya no termina en silencio: si hay fallos, muestra «X de Y entradas no se pudieron migrar», quita de la lista las que sí migraron y deja la lista lista para reintentar solo las fallidas (conservan su coincidencia). Antes navegaba hacia atrás aunque la mitad del lote hubiera fallado |
| `9758ddc` | Nombres de los textos del diálogo de fallos corregidos (moko: puntos → guiones bajos); detectado en la compilación local de verificación |

## 2. «Actualización general» del 03/11 (ya publicada y en el teléfono del usuario)

| Commit | Qué hace | Pendiente TRASPASO anterior que cubre |
|---|---|---|
| `1bdd3e9` | El visor androidx pide `loadPage` por página de la ventana, precarga el siguiente capítulo en las últimas 5 páginas y reporta la página visible más profunda: la lectura online deja de quedarse en «cargando», el capítulo siguiente se precarga y el capítulo se marca leído como en el visor stock | Reportes del teléfono del 03/11 (no estaba en la lista §6) |
| `d64f550` | `MigrateMangaUseCase` lanza `IllegalStateException` si la fuente destino no está instalada (antes retornaba en silencio y la migración se marcaba como hecha sin copiar nada) | §6.4 |
| `bf779ad` | Un fallo de red en la búsqueda ya no se muestra como «sin coincidencia»: la lista de migración tiene estado `Error` con «Reintentar búsqueda» y el reparador tiene `SEARCH_ERROR` con botón «Reintentar»; los ítems con error no los descarta «ocultar sin coincidencias» | §6.6 |
| `1ac9f25` | Grises e invertidos conectados a los visores androidx: uniform `colorOp` en el shader (mismo orden que el ColorMatrix stock: saturación 0 y luego inversión), los visores empujan los valores por frame y un efecto Compose redibuja al cambiar la preferencia | §6.2 |
| `d1a939f` | Espacio lateral webtoon aplicado en la tira androidx: las páginas se dibujan centradas con el margen por lado del visor stock; re-layout + re-anclaje en vivo al cambiar la preferencia | §6.3 |
| `7c3dff0` | La escala de tira del visor HQ (`continuousMinWidth`, «Min width», 1-100) también se aplica en la tira androidx (100 = sin cambio) | §6.1 (interpretación: la escala «Min width» era lo único llamable «escala» de la tira; si «Inteligente» se refería a otra cosa, revisar) |
| `be5c763` | Formato spotless (3 archivos arrastrados de merges anteriores + 3 del parche) | — |
| `5f82393` | Los helpers nuevos que consultan el `SourceManager` pasan a suspend | — |

## 3. Aplicado y compilado en CI

- **CI verde anterior: Run 41720335566 sobre `41a3c74` (conclusion: success)** — la build que el usuario tiene instalada. Run 41716634026 (`5f82393`) también verde.
- Ciclo del 04/11 (`e370cb0` + `0800479` + `9758ddc`): **compilación local de verificación ✅** (`:app:compileReleaseKotlin` y `spotlessCheck` en verde antes de empujar); el run de CI se dispara con el push de este traspaso (resultado pendiente al escribir esto).
- Verificación local del ciclo anterior (no sustituye CI ni teléfono): `spotlessCheck` ✅, `compileReleaseKotlin` ✅, `:app:testDebugUnitTest --tests TitleMatcherTest` ✅ 11/11 (la suite completa no cabió en el sandbox: 4 GB RAM, se atascó; CI no ejecuta tests).

## 4. Confirmado por el usuario en su teléfono

- En tira vertical, deslizar hacia arriba sube la página y hacia abajo la regresa (verificado en ambos modos).
- El menú del lector abre y se cierra.
- Las zonas de toque funcionan en modo «Por páginas».
- **Nuevo (04/11, build `41a3c74`):** el cambio de capítulo al deslizar al final del capítulo ya funciona, y el capítulo se marca como leído — los dos problemas reportados el 03/11 están resueltos según el usuario.
- **Nuevo (04/11):** la búsqueda de migración encontró candidato en las fuentes destino para 12/12 mangas (con porcentajes visibles); la prueba se abortó a propósito antes de confirmar, así que la migración real en sí sigue sin ejecutarse en el teléfono.

## 5. Pendiente de confirmar en teléfono

- De la actualización general del 03/11: lectura online sin «cargando» infinito (el usuario no lo volvió a reportar, pero tampoco lo confirmó explícitamente), grises/invertidos, espacio lateral, escala de tira, reintentos de búsqueda en migración/reparador, migración a fuente no instalada que falla con aviso.
- Del ciclo del 04/11: insignia «ÚNICO RESULTADO» en las parejas donde la fuente devolvió un solo resultado, y diálogo de resumen de fallos de la migración masiva.
- La migración real (confirmar y completar al menos una) sigue sin hacerse: los datos (categorías, leídos, marcadores) no se han validado en el teléfono.
- El crash de 274.869 px en el visor androidx: falta reproducirlo en el teléfono para confirmar si sigue ocurriendo.

## 6. Pendientes de código, en orden

1. ~~Escala «Inteligente» en tira~~ → aplicada como «Min width» (`7c3dff0`); si el usuario quería otra cosa, reabrir.
2. ~~Grises e invertidos sin conectar al visor WebGPU~~ → aplicada (`1ac9f25`).
3. ~~Espacio lateral en tira sin aplicar~~ → aplicada (`d1a939f`).
4. ~~`MigrateMangaUseCase` sale sin error si la fuente destino no está instalada~~ → aplicada (`d64f550`).
5. La migración no es atómica (varias escrituras separadas). Sin cambio: requiere un diseño propio (transacción por pasos + compensaciones) y aprobación del usuario.
6. ~~Un fallo de red en el reparador se muestra como «sin coincidencia»~~ → aplicada (`bf779ad`).
7. Decisión pendiente del usuario: si la migración masiva debe preseleccionar solo coincidencias exactas. La vía restante de falsos 100 % (auto-aceptación de resultado único) ya se identifica en pantalla con «ÚNICO RESULTADO» (`e370cb0`); falta la prueba de los 9 mangas (§8) para decidir si se endurece más la preselección.
8. ~~La migración masiva termina en silencio aunque haya fallos~~ → aplicado (`0800479`); pendiente de confirmar en teléfono.

## 7. Lo que NO se debe hacer sin confirmación del usuario

- Revertir commits.
- Cambiar la pantalla de ajustes.
- Tocar el modo por páginas.
- Regla general de la rama: no aplicar ni empujar ningún cambio que no esté aprobado explícitamente (un commit por cambio aprobado, para poder revertirlos por separado).

## 8. Pruebas pendientes del usuario (pasos concretos)

1. Instalar el APK arm64 del run de CI de este ciclo (commit `0800479`) — instalación completa, ~1 hora. Ya confirmado antes (no repetir): cambio de capítulo al deslizar y marcaje de leído ✅.
2. Lector online (tira): abrir un capítulo en línea y comprobar que las páginas cargan al avanzar (ya no se queda en «cargando»).
3. Grises e invertidos: activar «Escala de grises» y «Invertir colores» en los ajustes del lector con el visor androidx activo: debe aplicarse al instante en ambos modos (paginado y tira).
4. Espacio lateral y escala: en ajustes webtoon, mover «Espacio lateral» (0-25 %) y en ajustes del visor «Min width» (1-100 %): la tira debe estrecharse/centrarse en vivo y mantener la página actual.
5. Repetir la búsqueda de migración con los 9 mangas de referencia —entre ellos ZomGan, Zui Qiang Shen y Zettai ni Damage wo…— y revisar la insignia de cada pareja. En la prueba del 03/11 con 12 mangas todas encontraron candidato; en esta build, una pareja con título distinto debe mostrar «ÚNICO RESULTADO» (ámbar), no «100 %».
6. Resultados de referencia (simulación local en PC/JVM, **no verificados en teléfono**):
   - ZomGan → LectorXD: ~100 % (coincidencia real; debe seguir alta).
   - Zui Qiang Shen → Feng Shen Ji (Kumanga): ~0 % tras el parche (antes mostraba un 100 % falso).
   - Zettai ni Damage wo… → Level Drain (Emperor Scan): ~4,5 % tras el parche (antes mostraba un 100 % falso).
   - Umbral de la migración masiva: 40 %.
7. Reintento de búsqueda: con datos móviles cortados, lanzar una búsqueda en el reparador o migración: debe verse «Error de búsqueda» con botón Reintentar (no «sin coincidencia»). Reconectar y reintentar: debe buscar de verdad.
8. Reintento de migración masiva (nuevo): si el diálogo dice «X de Y entradas no se pudieron migrar», la lista queda solo con las fallidas y «Migrar» reintenta solo esas.
9. Primera migración REAL de un manga (elegir uno con EXACTO): confirmar y revisar en la ficha nueva que conservan categorías, capítulos leídos y marcadores. Así se valida el flujo completo que la prueba del 03/11 dejó sin ejecutar.
10. Si alguna pareja vuelve a mostrar un 100 % sospechoso, anotar el título exacto guardado en la biblioteca para poder depurarlo.

## 9. Notas de la sesión del 04/11 (tarde) — solo consultas, sin cambios de código

- **Run 41736690161 (commit `69769b1`): VERDE** (comprobado en la web de GitHub Actions). APK arm64 descargado del artefacto y entregado por el chat como `MihonFix-0.21.3-build-69769b1-arm64.apk` (~56 MB, desminificado).
- La tarea programada del chat para descargar ese APK (run 41736690161) se quedó atascada sin ejecutarse nunca (run_count 0); se borró y la descarga se hizo a mano. Si se usa una tarea programada de nuevo, comprobar al día siguiente que de verdad corrió.
- **Explicado al usuario (sin código):** (1) el porcentaje de actualización de biblioteca es «series revisadas / total» y las series se revisan por fuente, de la más grande a la menor, una por una dentro de cada fuente: el tramo final lo dominan las fuentes lentas, con límite de peticiones y tiempos de espera de red; (2) el «colgado en 100 %» corresponde a la fase de cierre que el porcentaje no cuenta: guardar errores, montar las notificaciones de capítulos nuevos (una portada por serie, en serie, contra los sitios lentos) y arrancar la descarga automática; (3) la notificación de errores de actualización sigue enviándose (ruta intacta en el worker) pero su canal es IMPORTANCE_LOW y One UI puede ocultar las notificaciones silenciosas o tener la categoría apagada: revisar Ajustes → Aplicaciones → MihonFix → Notificaciones → grupo «Biblioteca». Si el usuario quiere, propuesta futura: canal de errores con importancia por defecto (requiere ID de canal nuevo para instalaciones existentes; necesita aprobación).
- **Reparador de enlaces:** usa el mismo motor que la migración (pase estricto + pase profundo con variantes del título y TitleMatcher), pero busca el título GUARDADO en la MISMA fuente. Un cambio de nombre total (sin palabras en común) es imposible de emparejar por texto; para eso sirve Migrar con el nombre nuevo a mano.
- Espacio del proyecto liberado: borrados los dos .deb de OpenJDK (121 MB) que quedaron de la instalación de Java. Proyecto en ~455 MB de 2 GB. El ZIP del artefacto CI (218 MB) se descargó en /tmp y ya se borró.

## 10. Feedback del usuario del 04/11 (noche) — corrección de teléfono y datos reales de migración

- **Teléfono: Xiaomi POCO F6 (HyperOS/MIUI), operadora Claro BR.** La nota anterior sobre One UI/Samsung era un supuesto erróneo; el consejo correcto es MIUI: batería «Sin restricciones» para MihonFix, inicio automático permitido, y revisar la categoría de notificación «Errores de actualización» en Ajustes → Aplicaciones → MihonFix → Notificaciones.
- **Observaciones del usuario sobre la notificación de progreso** (capturas del 03/11 21:22): al 12–18 % lista 4 títulos a la vez; hacia el final 1 a la vez; orden alfabético por web. Coincide con el código: fuentes en paralelo (semáforo de 5), cada web procesa sus títulos A→Z, y el tramo final es una sola web lenta.
- **Datos reales de migración del usuario (~100 mangas migrados):** ~80 correctos (a veces ±1-2 caps), ~10 coincidencias falsas («alucinaciones» de búsqueda), ~10 con el mismo nombre pero 30-40 caps menos (a veces solo 1). En la búsqueda manual suele existir la versión completa en OTRAS extensiones con nombre distinto (ej. ficticio «Jugador» vs «Player»). El emparejador solo compara títulos, nunca cantidades de capítulos; el reparador solo busca en la fuente original con el título guardado.
- **Verificado en código:** las filas de la lista de migración (`MigrationListScreenContent.kt`) muestran fuente + último capítulo del manga ORIGINAL, pero NO el total de capítulos del candidato: la trampa «mismo nombre, menos caps» es invisible antes de confirmar.
- **Propuestas hechas al usuario (PENDIENTES de su aprobación; sin código aplicado):**
  1. Informe de fuentes difíciles al terminar la actualización (web + fallos + duración por web), registrando duración media por fuente en SourceHealthStore. Sin cambio de orden.
  2. Lista de migración: mostrar el total de capítulos del candidato en cada fila (comparar contra el original).
  3. Migración masiva: preseleccionar solo EXACTO y dejar las aproximadas sin marcar (los ~10 % de coincidencias falsas del usuario apoyan la decisión pendiente §6.7).
  4. (Opcional, con datos de 1) Cambiar el orden de actualización a «fuentes problemáticas primero», adaptativo por ronda. Riesgo explicado al usuario: el tiempo total no baja y, si MIUI corta la actualización a medias, las fuentes sanas del final quedan sin revisar; por eso se propone decidirlo con datos del informe.

---

Nota de seguridad: este documento se revisó antes de guardarse; no contiene tokens, claves, contraseñas ni URLs con credenciales.
