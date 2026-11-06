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

## 11. Ciclo del 04/11 (noche tardía) — regla de compilación e informe de fuentes

- **REGLA NUEVA DEL USUARIO (obligatoria a partir de ahora):** la máquina del chat tiene 4 GB de RAM y compilar la app ahí queda PROHIBIDO. Todas las compilaciones se hacen exclusivamente en GitHub Actions; la cuota del usuario alcanza para varias builds. Preferencia explícita: «prefiero que se haga bien a que se haga rápido», reducir la carga de la máquina virtual es prioridad. Lo que SÍ se hace en local: editar código, leer/analizar, validar XML, commitear y empujar. Ya no se ejecuta `spotlessCheck`, `compileReleaseKotlin` ni tests locales como verificación previa: la revisión es manual línea a línea y el CI es el árbitro.
- **Actualización (mismo 04/11, mensaje posterior del usuario):** regla relajada — «compilar en local es importante y evita errores; puedes hacer tu compilación previa antes de empujar a GitHub, pero recuerda eficiencia por sobre todo». La verificación local previa vuelve a ser recomendada (ver receta ajustada en §12); CI sigue siendo el árbitro final.
- **Propuesta 1 IMPLEMENTADA (commit `1366d05`, pendiente de CI verde y de prueba en teléfono) — SUPERADA por la versión corregida de §12 (`7718f3e`):** el CI detectó en `1366d05` un error de suspend en el notificador y el fix se perdió con el reciclaje del sandbox; la versión de §12 lo rehace ya corregido y verificado en local.
  - `SourceHealthStore` guarda por fuente la estadística de la última pasada completa: series intentadas, fallos y duración total de su cola (`SourceUpdateStat`, serializada igual que los registros de salud). Las pasadas interrumpidas no se registran.
  - Al terminar la actualización, el worker escribe en el mismo archivo de errores una sección «Fuentes con problemas» (series, fallos, ~segundos por serie, peor primero) y, si NO hubo fallos de series, envía además una notificación en el canal de errores con la lista de fuentes problemáticas (si hubo fallos, la notificación de errores ya existe y el informe solo va al archivo). Umbral: media ≥ 10 s por serie o algún fallo.
  - Sin cambios de orden ni de canales: el informe sale por el canal existente «Errores de actualización» (IMPORTANCE_LOW), que el usuario ya sabe revisar en MIUI.
  - Verificado a mano en esta sesión (no hay compilación local): imports completos, APIs existentes (`getObjectSetFromStringSet`, `getAndSet`, `openErrorLogPendingActivity`, `notify`), XML válidos, claves moko coincidentes base/es, y que `getOrStub` devuelve la interfaz de source-api cuyo `toString()` de `HttpSource`/`StubSource` ya imprime «Nombre (ES)».
- Las propuestas 2, 3 y 4 de §10 siguen pendientes de aprobación del usuario.

---

## 12. Ciclo del 04/11 (noche) — Propuesta 1 reconstruida y corregida: informe de «Fuentes con problemas»

Contexto: la implementación de `1366d05` llegó a GitHub pero con un error de compilación (`getOrStub` suspend dentro de una función no suspend en el notificador; CI rojo, run 41750547572). El fix y la copia local se perdieron al reciclarse el sandbox. Se rehízo la implementación completa sobre el árbol actual, ya corregida, y se verificó en local.

Qué hace (todo dentro de la actualización de biblioteca, sin pantallas nuevas):

- Al terminar un pase completo (no vale para actualizaciones de una categoría o una sola fuente), cada fuente deja guardadas sus estadísticas del último pase: series intentadas, fallos y duración total (`SourceUpdateStat` en `SourceHealthStore.kt`, nueva preferencia `source_health_last_pass_update_stats`). Los pases interrumpidos no se registran.
- En el archivo de errores de siempre (`mihon_update_errors.txt`) se añade una sección «Fuentes con problemas»: `Nombre (idioma) — N series, N fallos, ~N s/serie`, peor fuente primero. Si no hubo series fallidas, se crea el archivo solo con esa sección.
- Notificación nueva (mismo canal «Errores de actualización», IMPORTANCE_LOW, amable con MIUI) SOLO cuando no hubo series fallidas: «N fuentes dieron problemas en la actualización» con hasta 5 fuentes en el cuerpo; al tocarla se abre el archivo con el informe. Si hubo series fallidas, la notificación de errores de siempre ya lo cubre.
- Umbral de «fuente con problemas»: cualquier fallo o media de 10 s/serie o más.

Compilación local: **verde** (`spotlessCheck` + `compileReleaseKotlin`, 1 commit de código + 1 de docs; CI se dispara con el push). Receta ajustada del sandbox (importante para próximas sesiones):

- El `-Xmx4g` de `gradle.properties` mata al daemon en esta máquina de 3,9 GB (el kernel lo cortea a mitad de la compilación de `:app`, error «Gradle build daemon disappeared»). No tocar `gradle.properties` (es de CI); en local usar: `--max-workers=1 -Dorg.gradle.jvmargs="-Xmx2560m -XX:MaxMetaspaceSize=768m" -Dkotlin.compiler.execution.strategy=in-process`.
- Antes de compilar, matar procesos java huérfanos: un demonio Kotlin de un intento anterior puede quedarse con ~2 GB y provocar el OOM del siguiente intento (`kill -9` de los PID java que no sean el cliente).
- Nueva trampa de Kotlin documentada: no llamar funciones suspend dentro de la lambda de `context.notify` (no es inline → «Suspension functions can only be called within coroutine body»); calcular textos antes del bloque.

Pendiente de confirmar en teléfono (cuando toque actualización de biblioteca completa): ver la notificación «N fuentes dieron problemas…» solo si no hubo series fallidas, y el archivo de errores con la sección de fuentes.

Sin cambios: las propuestas 2-4 de la sesión anterior (totales de capítulos en la lista de migración, preselección solo EXACTO, orden adaptativo) siguen pendientes de aprobación.

---

## 13. Cierre del ciclo del 04/11 (madrugada) — CI verde y APK entregado

- **CI verde:** run `41768128834` (Build APK) sobre `d37ffb3`, success el 04/11. Incluye la Propuesta 1 corregida (informe «Fuentes con problemas», fix suspend del notificador).
- **APK entregado:** `download/MihonFix-0.21.3-build-d37ffb3-arm64.apk` (arm64-v8a release, 37,2 MB). Verificado: zip íntegro, 3 dex, AndroidManifest presente, `lib/arm64-v8a/libwebgpu_c_bundled.so` incluida (build WebGPU correcta). Nota: pesa 37,2 MB, no los >40 MB estimados; el tamaño es normal para release minificada por ABI.
- **Pendiente de prueba en el POCO F6 (cuando el usuario toque actualización de biblioteca completa):** notificación «N fuentes dieron problemas…» solo si no hubo series fallidas; archivo `mihon_update_errors.txt` con la sección de fuentes problemáticas.
- Propuestas 2, 3 y 4 de §10 siguen pendientes de aprobación del usuario.

---

## 14. Ciclo del 04/11 (mañana) — Propuestas 2 y 3 implementadas, CI verde, APK entregado

- **Propuesta 2 IMPLEMENTADA (`c4ef0a7`):** en la lista de migración, la insignia de capítulos del candidato ahora se pone ROJA y muestra «candidato / original» cuando el encontrado tiene MENOS capítulos que el original (la trampa «mismo nombre, menos caps» se ve antes de confirmar). Si tiene igual o más, queda la insignia normal de siempre. Implementado en `MigrationListScreenContent.kt` (`MigrationListItem`/`MigrationListItemResult` reciben `originalChapterCount`).
- **Propuesta 3 IMPLEMENTADA (`1a39b74`):** la migración masiva solo procesa coincidencias de título EXACTAS y las elegidas a mano por el usuario (`similarity == null`); las aproximadas (% y ONLY RESULT) quedan sin migrar en la lista para revisión manual (portada, conteo, «Migrar ahora» por fila). El diálogo de confirmación cuenta lo que se va a migrar de verdad y avisa cuántas se omiten y por qué (texto nuevo en base y español); el flag vive en `MigratingManga.SearchResult.Success.isPreselected` y el campo del diálogo pasó de `totalCount` a `migratingCount`.
- **Verificación local:** `spotlessCheck` + `compileReleaseKotlin` verdes para cada commit por separado (1m38s y 1m52s, receta §12).
- **CI verde:** run `41801017904` (Build APK) sobre `1a39b74`, success el 04/11. Sin errores de CI.
- **APK entregado:** `download/MihonFix-0.21.3-build-1a39b74-arm64.apk` (arm64-v8a release, 37,2 MB; zip íntegro, `libwebgpu_c_bundled.so` incluida). Incluye también todo lo de §13 (informe de fuentes con problemas).
- **Pendiente de prueba en el POCO F6:** (a) fila de migración con insignia roja «N / M» cuando el candidato tiene menos caps; (b) migración masiva que omite las aproximadas y diálogo «Se omitirán N elementos (sin coincidencia o coincidencia no exacta)»; (c) lo del §13 (informe de fuentes). Caso límite conocido: si NINGUNA entrada es exacta, el diálogo dirá «Migrar 0 elementos?» — cancelar y revisar a mano.
- Propuesta 4 (orden adaptativo) sigue pendiente: se decidió esperando datos del informe de fuentes (§10.4).

---

## 15. Fix del lector: salto a la última página al cruzar de capítulo (b681b70)

- Reporte del usuario (04/11): en tira continua, al pasar al siguiente capítulo lo mandaba directo a la última página. Aclaración posterior del usuario: ocurrió UNA sola vez, no se ha repetido, y desapareció al cambiar a tira discontinua; sigue probando en `1a39b74` para confirmar.
- **Causa (`b681b70`):** al cruzar al siguiente capítulo la tira se reconstruye con un prefijo distinto (se suelta el capítulo anterior de arriba) y todos los offsets por debajo se desplazan mientras `scrollPos` no se mueve: el viewport aterrizaba profundo en el capítulo nuevo y se clavaba al final del documento (su última página). Afectaba a webtoon y vertical continua (misma clase de visor, `AndroidxWebGpuContinuousViewer`); la tira discontinua usa otra clase y no se veía afectada — consistente con lo observado por el usuario. Esporádico porque depende del momento exacto del cruce y de cuánto se había precargado.
- **Fix:** `onChaptersChanged` compensa el scroll con el desplazamiento que sufrió el offset de la página actual (la tira queda «pegada» al intercambio); el loader devuelve temprano para capítulos ya cargados, así la identidad de páginas (y aspectos) sobrevive al intercambio y el ancla es estable.
- Verificación local OK (1m38s, receta §12); CI verde run `41810196083`.
- **APK entregado:** `download/MihonFix-0.21.3-build-b681b70-arm64.apk` (39,0 MB / 37,2 MiB; zip íntegro, 3 dex, `libwebgpu_c_bundled.so` incluida).

---

## 16. Función nueva: activar/desactivar fuentes en lote por categoría (350a7dc)

- Petición del usuario (04/11, con captura de la pantalla de fuentes): con casi 100 fuentes, quería un botón para activar/desactivar fuentes por categoría en vez de marcar una por una.
- **Implementado (`350a7dc`):** la barra superior de «Clasificación personal de fuentes» ahora tiene menú ⋮ con «Activar las fuentes de la lista» / «Desactivar las fuentes de la lista» y un diálogo de confirmación que muestra cuántas fuentes se verán afectadas (cadenas nuevas `source_filter_bulk_*` en base y español). Combinado con los chips de filtro de categoría funciona como interruptor en lote por categoría: filtrar (p. ej. Adulto) → ⋮ → activar/desactivar → confirmar. Sin filtro activo afecta a TODAS las fuentes listadas (por eso el diálogo pide confirmación con el número).
- Archivos: `SourcesFilterViewModel.kt` (`setAllVisibleSources`, reutiliza la sobrecarga en lote de `ToggleSource`: una sola escritura de preferencia para todo el lote), presentación `presentation/browse/SourcesFilterScreen.kt` (composable `BulkActionsMenu`), cableado `ui/browse/source/SourcesFilterScreen.kt`, i18n base+es.
- Verificación local OK (1m56s, receta §12); CI verde run `41812966595`.
- **APK entregado:** `download/MihonFix-0.21.3-build-350a7dc-arm64.apk` (39,0 MB / 37,2 MiB; verificado igual que §15). Incluye TODO lo anterior: Propuestas 1-3 + fix del lector + esta función.
- Pendiente de prueba en el POCO F6: filtrar una categoría (p. ej. Adulto) → ⋮ → «Desactivar las fuentes de la lista» → confirmar; todas las casillas de esa categoría deben quedar desmarcadas y el cambio debe sobrevivir al reinicio de la app.

---

Nota de seguridad: este documento se revisó antes de guardarse; no contiene tokens, claves, contraseñas ni URLs con credenciales.

## 17. Ciclo del 04/11 (tarde) — visor optimizado e incidente del reciclado

- **Feedback del usuario sobre el visor WebGPU:** mejor calidad de imagen (confirmada), pero (a) el capítulo tarda más en abrir porque «carga varias páginas a la vez» y (b) pequeños tirones al desplazarse por la tira.
- **Causa (código):** el visor lanzaba TODA su ventana (actual + 2 atrás + 5 adelante = hasta 8 decodificaciones) en paralelo y sin orden: la página visible competía por CPU/GPU contra 7 vecinas y cada tick de scroll que traía una página nueva disparaba su decodificación junto a las demás.
- **Optimización (`dd8d4d8`):** `updatePageWindow` presenta primero la página visible y las vecinas pasan por un semáforo (`preloadGate`, de a una en fondo); solo la visible se salta la cola. Primera imagen antes, scroll más parejo. Compromiso: las vecinas tardan algo más en quedar listas si se scrollea muy rápido (se muestra su spinner por diseño).
- **Incidente — reciclado de la máquina virtual:** la sandbox se recicló y restauró /home/z/my-project de una instantánea de la MADRUGADA (~01:56): el repo local perdió TODA la mañana (7718f3e..1284d6b, a salvo en origin) y scripts/download volvieron atrás. Efecto colateral confuso: al verificar sobre el árbol viejo, `compileReleaseKotlin` falló en LibraryUpdateNotifier con NON_LOCAL_SUSPENSION_POINT — era el código VIEJO del informe (1366d05/a07321d), que la reconstrucción de la mañana (7718f3e, tras el CI rojo 41750547572 ya registrado) había reemplazado; el error no existía en el árbol bueno. Trampa nueva documentada: **la lambda de `joinToString` NO es inline** (a diferencia de `map`/`forEach`) y no admite llamadas suspend dentro — usar `map { }.joinToString()`.
- **Recuperación:** rama de respaldo con los 3 commits del día → `git reset --hard origin/feature/webgpu-clean` → cherry-pick SOLO del fix del visor (los otros 2 commits arreglaban código viejo ya descartado). **Lección permanente: tras un reciclado, comparar `git log` local vs origin ANTES de commitear nada.**
- **Entorno re-montado:** JDK 21.0.13+9 (Temurin), cmdline-tools + platforms;android-37.2 + build-tools;37.0.0, Gradle 9.8.2 (re-extraer el zip: la primera extracción quedó trunca). Receta §12 funciona igual pero con `-Xmx2048m`: con 2560m el demonio murió por RAM durante la compilación completa desde cero (~4 min la primera vez por re-descarga de dependencias, luego ~2 min).
- **Verificación local:** BUILD SUCCESSFUL (2m03s). **CI verde:** run `41835209278` sobre `dd8d4d8`.
- **APKs entregados en download/ (los de hoy se re-descargaron de sus runs verdes tras el reciclado):** `MihonFix-0.21.3-build-dd8d4d8-arm64.apk` (EL RECOMENDADO: Propuestas 1-3 + fix del lector + fuentes en lote + visor optimizado), y re-puestos `build-b681b70` y `build-350a7dc`.
- **Pendiente de prueba en el POCO F6:** (a) apertura de capítulo más rápida en tira continua; (b) menos tirones leyendo la tira; (c) re-confirmar que el fix del salto a última página sigue bien (dd8d4d8 lo incluye).

---

## 18. Ciclo del 05/11 — barras negras, calor del visor androidx y CI solo-app

- **Feedback del usuario (05/11):** (a) el parche de calor `dd8d4d8` no fue efectivo — el visor experimental volvió a calentar gravemente el POCO F6 y volvió al estable; (b) las barras negras entre página y página están en los DOS visores webgpu; (c) cada build compila también las extensiones — evitarlo, solo Mihon; (d) LeerCapítulo se deja como está (la fuente funciona, no hay más que hacer). Además: revisar los dos visores webgpu y llevar a los nuevos los motivos sólidos por los que el visor original está optimizado desde hace años.
- **Diagnóstico (código propio + fuente de la librería `ca.mpreg:webgpuviewer` clonada en tag 54 — es MIT y público en `github.com/mpreg-ca/webgpuviewer`):**
  - *Barras negras:* el color con el que el surface se limpia donde una página no cubre. El renderer androidx limpiaba a negro puro siempre (huecos entre páginas, espacio lateral, páginas aún sin textura — la zona del spinner). El HQ pasaba `backgroundColor=null` con tema automático y la librería limpia ese caso a negro transparente (barras alrededor de páginas encajadas y tras recortes). El lector stock muestra el fondo del tema en todos esos casos.
  - *Calor:* `requestRender` dibujaba un frame GPU completo por llamada — un tick de scroll, un paso de gesto, una subida de textura — cada uno con su corrutina+mutex+render+present, acumulándose tras el mutex y drenando frames atrasados mucho después de parar la interacción. `dd8d4d8` ordenó los preloads (apertura/tirones), pero el calor vivía en el camino de render.
  - *El «motivo sólido» del visor original:* renderiza a través del sistema de vistas de Android — invalida y el RenderThread dibuja como mucho un frame por vsync y NADA en reposo. La librería ca.mpreg implementa exactamente esa disciplina en `ImageViewerState.collect()` (flag sucio + canal conflated + reloj de frames; el bucle se estaciona cuando nada está sucio). Eso es lo que se portó.
- **Commits (uno por cambio aprobado):**
  - `73af79d` ci: compilar solo la app en cada build — `:app:assembleRelease` en los dos jobs de la app; job `changes` + entrada `build_extensions` en workflow_dispatch para las extensiones.
  - `4d1a9af` fix(reader): fondo del lector tras las páginas — `SurfaceHost` empuja el color del tema al renderer androidx (colectado del StateFlow; un cambio de tema a mitad de lectura re-limpia el surface) y el HQ pasa siempre `readerBackgroundColor()` a `Image` (con automático era null → negro). `applyColorFilter` cachea grises/invertidos. Staging parcial por hunks para separar este fix del pacing en el mismo archivo; el estado intermedio se compiló antes de commitear.
  - `508f0c4` perf(reader): frame pacing en el visor androidx — se porta `collect()` de ca.mpreg: `requestRender` solo marca sucio y despierta; un bucle (Choreographer sobre Main) dibuja ≤1 frame por vsync por muchos requestRender que lleguen y se estaciona limpio (cero ticks de Choreographer y cero GPU en reposo); un dibujo en vuelo retiene el flag para el frame siguiente. `stripPageWidthPx` cachea espaciado lateral y escala de tira (corría por página y por tick de scroll contra las preferencias). Reloj de frames compartido en `AndroidxWebGpuFrameClock.kt` (antes privado del visor continuo).
  - `8ffcbb9` ci: la detección de cambios pasa por la API de compare — `github.event.commits` llega al contexto de Actions SIN las listas de archivos de cada commit (verificado en el log del run `42070886900`), así que la primera versión nunca detectaba nada; ahora se pregunta a `compare/before..after` con el token automático, y si el compare falla o trunca las extensiones se compilan igual (el lado seguro es una build de más, no un APK de extensión viejo).
- **CI:** run `42070886900` (508f0c4) verde en ~17 min: `build_release` + `build_unminified` + `changes`, los dos jobs de extensión **skipped** (nada de extensiones cambió). Run `42073328119` (8ffcbb9) verde con los cinco jobs, extensiones incluidas (el push tocó el workflow y el detector nuevo lo vio) — ambos caminos del gating quedan probados.
- **APK entregado:** `download/MihonFix-0.21.3-build-508f0c4-arm64.apk` (39,0 MB; zip íntegro, 3 dex, `lib/arm64-v8a/libwebgpu_c_bundled.so`, y el bytecode nuevo verificado en dex: clase FrameClock y setBackgroundColor). El código de la app no cambia después de `508f0c4` (los commits posteriores son solo workflow/docs).
- **Pendiente de prueba en el POCO F6:** (a) barras negras: leer tira (con y sin huecos) y paginado con el tema de siempre — huecos, espacio lateral y zonas de spinner deben mostrar el fondo del lector, no negro; también con espacio lateral > 0; (b) calor: leer un rato con el visor experimental (androidx) — debe mantenerse templado, y en reposo no debe calentar nada; (c) grises/invertidos siguen aplicándose al cambiarlos a mitad de lectura.
- **Notas para futuras sesiones:**
  - `ca.mpreg:webgpuviewer` 55-58 trae mejoras de rendimiento relevantes para el HQ (pacing de tiles, TransientAttachment, reúso de pipelines, samplers para el resize), pero cambia API que usamos (`Image.x/y` → `offsetX/offsetY`, `trim` → `visibleTrim`, `isPanning` fuera, nuevos `enableTiles`/`holdUntilFinal`): el bump es un ciclo propio si el usuario lo aprueba.
  - El entorno local se re-montó (JDK 21.0.13+9 + cmdline-tools + platforms;android-37.2 + build-tools;37.0.0 bajo `tools/`, `local.properties` apunta ahí). Maven Central devuelve 429 desde el sandbox: compilar con `--init-script /home/z/my-project/scripts/maven-mirror.init.gradle` (espejo Aliyun + google directo; nunca se pasa en CI). Con `-Xmx1536m` el daemon sobrevive; con 2048 murió una vez con daemons huérfanos vivos — matar javas antes de compilar.
  - `toJSON(github.event.commits)` NO sirve para detectar archivos cambiados: el contexto de Actions recorta esas listas. Usar la API de compare (ya en el workflow).

## 19. Ciclo del 06/11 — actualizaciones cada 6 h y firma estable (instalar sin desinstalar)

- **Pedido del usuario (06/11):** (a) en Ajustes → Biblioteca, el intervalo mínimo de actualización automática era 12 h — agregar 6 h; (b) poder actualizar la app sin desinstalarla en cada build.
- **6 horas (`5485a46`):** una línea en `SettingsLibraryScreen.kt` — el map de intervalos gana `6 to MR.strings.update_6hour`. El string ya existía en base/es/pt-rBR (los ajustes de datos lo usan para la frecuencia de backup) y `LibraryUpdateWorker.setupTask` acepta cualquier cantidad de horas, así que no tocó nada más.
- **Firma estable (`ef0ece3`):** diagnóstico — el repo no tenía secrets de firma, la CI caía al fallback «debug signing» y cada runner genera su `debug.keystore` aleatorio → cada APK con firma distinta → Android rechaza la instalación encima. Solución:
  - Keystore RSA-2048 PKCS12 (validez 30 años, alias `mihonfix`) generado fuera del repo y subido como secrets `KEYSTORE_BASE64`/`KEYSTORE_PASSWORD`/`KEY_ALIAS`/`KEY_PASSWORD` vía API (sealed box). El repo es **público**: el keystore jamás va a git. `:app` ya tenía el hookup (`MIHON_GITHUB_RELEASE` → repunta la config debug); lo que faltaba eran los secrets.
  - Los módulos de extensión replican ese hookup (repuntan su config debug al keystore de CI) y sus jobs del workflow ganan el paso `Setup Signing Keystore`.
  - Bump de extensiones a 1.6.73 / 1.6.78 (106073/106078) para que la build re-firmada se ofrezca como actualización.
  - `versionCode` de la app sigue en 39: igual versionCode + misma firma = reinstalación válida; el identificador de build vive en el versionName (contador de commits).
- **Migración del usuario (UNA vez):** la app y las extensiones instaladas hoy están firmadas con claves debug aleatorias de runs viejos → hace falta desinstalarlas una última vez e instalar esta build; de ahí en adelante todo se instala encima. La biblioteca del usuario se respalda/restaura con el backup de Mihon (o se revalida al iniciar sesión en las fuentes) — recomendación dada: hacer backup de datos antes de desinstalar.
- **CI:** este push toca `build_apk.yml` y `extension-*/` → el detector compila también las dos extensiones; los tres APK salen firmados con la clave nueva (verificar en el log del job: «Keystore secret found. Configuring release signing.»).
- **Respaldo del keystore:** copia del keystore + contraseñas entregada al usuario fuera del repo (carpeta download del chat). Perderlo = volver al mundo de desinstalar todo; guardarlo.
- **Resultado de la CI:** la primera subida (run 42174981427, push) falló: el keystore se había generado con `-keypass` distinto al `-storepass`, pero en PKCS12 keytool ignora el keypass y protege la clave con el storepass → AGP moría con «Failed to read key … Given final block not properly padded». Regenerado el keystore con contraseña única (misma para store y clave, lectura probada con un main de Java imitando a AGP) y secrets actualizados. **Run 42176062965** (workflow_dispatch sobre `b9818fae`, con `build_extensions=true`): verde con los 5 jobs.
- **Firma verificada fuera de la CI:** los tres APK del run tienen el mismo certificado en su APK Signing Block v2 (fingerprint SHA-256 `DE:78:89:F3:…:D0`), idéntico al del keystore y con CN=MihonFix — verificado con un parser propio del signing block (lección: en v2 hay DOS niveles de secuencia length-prefixed antes del signer; la primera versión del parser hasheaba la firma RSA en vez del certificado).
- **Entregado en download/:** `MihonFix-0.21.3-build-ef0ece3-arm64.apk` (39,0 MB, zip íntegro, 3 dex, libwebgpu_c_bundled.so presente), `Tachiyomi-MHScans-1.6.73.apk`, `Tachiyomi-EmperorScan-1.6.78.apk` y `respaldo-keystore-mihonfix/` (keystore + contraseña + LEEME con instrucciones de restauración).
- **Token de GitHub:** rotado el 06/11 a un fine-grained PAT (push/actions verificados); vive en el remote del clon local, como siempre.

## 20. Confirmación del usuario (07/11) — visor experimental verificado en el POCO F6

- **Feedback del usuario (07/11):** los tres puntos pendientes del §18 quedan **verificados en el teléfono**: (1) barras negras resueltas — el fondo del lector se ve donde antes había negro; (2) calor resuelto — la lectura con el visor experimental (androidx) es fluida y el teléfono se mantiene templado, ya no vuelve al estable; (3) grises/invertidos funcionando a mitad de lectura.
- **Implicación:** el visor experimental androidx.webgpu queda como **lectura por defecto** usable en producción. El fix de frame pacing (`508f0c4`, porte de `collect()` de ca.mpreg) resultó efectivo, a diferencia del `dd8d4d8` anterior que solo ordenaba los preloads.
- **Lo que sigue para el visor** (sin orden todavía, pendiente de aprobación):
  - Bump `ca.mpreg:webgpuviewer` 54 → 55-58 (mejoras de rendimiento: pacing de tiles, TransientAttachment, reúso de pipelines, samplers para el resize). Cambia API — ciclo propio.
  - Limitaciones residuales del visor experimental: tira continua sin pinch-zoom, sin páginas de transición entre capítulos, saltos de layout al resolver proporciones; paginado con zonas de toque fijas y sin fling; reanudar cae en la primera página del capítulo.
  - Zero-copy AHardwareBuffer: parqueado hasta que la librería ca.mpreg entregue AHB desde el decoder.

## 21. Ciclo del 07/11 (tarde) — bump del visor HQ 54→58 + reanudación en el experimental

- **Feedback del usuario (07/11, tarde):** comparación entre visores en su POCO F6 — el **HQ reanuda** la página donde lo dejó, el **experimental no**; el HQ aún conserva el problema de las **barras negras** y parece conservar el del **calor**. Cada visor tiene justo lo que al otro le falta.
- **Decisión:** los dos más fáciles de probar en UNA build — (1) bump de la librería del HQ (ataca con upstream las dos quejas del HQ) y (2) fix de reanudación del experimental (autocontenido en nuestro código). Zero-copy sigue parqueado.
- **Bump `ca.mpreg:webgpuviewer` 54 → 58** (`36b990462`):
  - Upstream (`github.com/mpreg-ca/webgpuviewer`, MIT) trae en 55-58, leyendo el diff de fuentes tag a tag: pacing de tiles con cola temporizada contra la tasa de refresco del panel (`batchTargetNs`), `TransientAttachment` para tiles, reúso de pipelines en todo el proceso, reúso de tiles obsoletos en vez de descartarlos, sampler para el resize de tiles, disciplina hold-until-final (**opt-in, NO la activamos** — con nuestras páginas Compose, el spinner de carga podría congelarse; queda como palanca si las barras persisten), rework del cutout (`Fix cutout height at startup`, `displayCutoutTopPx` re-homing de páginas en home), `Crop trim for any image/page`, fix de última fila de bitmaps de tamaño impar (plausible causa de barras/negro al pie), y fixes de gestos (tap cancelado, fling tras pinch, quick-zoom, maxScale continuo).
  - **Empaquetado:** en 58 la librería ya NO incrusta `androidx.webgpu` en su AAR (eliminó su tarea de merge de AARs) y lo expone como dependencia `api` → en la app `compileOnly(libs.webgpu)` vuelve a ser `implementation(libs.webgpu)`. La 58 se compila contra `androidx.webgpu:webgpu:1.0.0-alpha06`, la misma versión que ya teníamos en el catálogo (el HQ en 54 usaba `dev06`; sus shaders nuevos usan `pass.setImmediates`, que existe desde alpha06).
  - **minSdk:** la librería sube a 26; la app ya estaba en 26 (`mihon.versions.toml android-sdk-min`) — sin impacto.
  - **API:** cero migración de código. Los renombres (`Image.trim`→`visibleTrim`, `Image.x/y`→`offsetX/offsetY`, `shownWidth/shownHeight`) son **internos**; todo lo que el fork toca (`ImagePage.ImageSingle.x/y/homeX/homeY/homeScale/animationTargetX/animateTo`, `Image.trim`, `GainmapInput`, `ImageFilterWgsl(source, label)` + `validate()` suspend, `ImageViewer/ImageViewerContinuous(modifier, state)`, `ComposePageSize`, `tileFilters`, `transitionFromPage`, `doubleTapGesturesEnabled`, `animatePageTurn`, `fetchPage`, `Dummy/ImageSpread/Compose`) existe con la misma firma en el tag 58 — verificado miembro a miembro contra el clon upstream (`/home/z/my-project/webgpuviewer-upstream`).
- **Fix de reanudación del experimental continuo** (`a5237598b`): diagnóstico — `setChapters` ancla bien en `requestedPage`, pero el `snapScrollTo` corría contra un layout construido a **1px** (`surfaceWidthPx` arranca en 1 antes del primer frame) y nada re-derivaba el scroll cuando la tira tomó sus números reales → viewport en el tope (página 1); además el tracking re-derivaba `current` de los números traspuestos y **reportaba (y salvaba) la página 1** sobre el progreso real. Solución: ancla de restauración pendiente (`anchorPending`) que cada `relayout` re-pega a la página objetivo hasta que esta queda en el centro del viewport sobre un surface de ancho real; el tracking no sigue ni reporta páginas transitorias mientras el ancla está en vuelo; el primer gesto del usuario cancela el ancla; `snapScrollTo` escribe directo (ya no pasa por la capa de gestos, que es donde el ancla cede); `onWindowChanged` ahora relayouta (el surface pudo reportar tamaño después del último layout). El paginado experimental no necesita nada: su `current` ES la página mostrada.
- **CI:** push `a5237598b` dispara el build (inputs por defecto; las extensiones no cambiaron). Resultado y artefactos: ver §21-bis al cerrar el ciclo.

## 21-bis. Cierre del ciclo del 07/11 (tarde) — CI y entrega

- **CI:** el primer run (push del código, `a5237598b`) quedó **cancelado** por el push de docs que lo pisó (concurrency del workflow); re-lanzado vía API `runs/{id}/rerun` (conserva el SHA original) → **run 42271630287 verde** (Build Release APK + Unminified OK; extensiones omitidas — no cambiaron). El push de docs se hace SIEMPRE después de que el run del código esté en vuelo o terminado, nunca antes.
- **Entrega:** `download/MihonFix-0.21.3-build-a523759-arm64.apk` (35,2 MB; arm64). Firma verificada fuera de CI: v2 `DE:78:…:6A:D0` (keystore estable, CN=MihonFix) → **se instala encima de la build `ef0ece3` sin desinstalar**. Empaquetado verificado: `libwebgpu_c_bundled.so` (ahora viene del AAR de androidx.webgpu alpha06) + nueva `libresize.so` de la 58; clases de webgpuviewer y androidx.webgpu presentes (3 dex).
- **Qué probar en el teléfono (una sola instalación):**
  1. **HQ** (Use high quality renderer): barras negras — abrir en modo nocturno, girar página, iniciar un capítulo; calor — 10-15 min de lectura continua comparando con la build anterior.
  2. **Experimental continuo**: salir a mitad de capítulo y reabrir → debe caer en la página guardada (y el progreso no debe reescribirse a la página 1).
  3. Regresión rápida del HQ paginado: zoom con pellizco, doble tap, tap de zonas.
- **Palancas si las barras del HQ persisten:** `holdUntilFinal = true` en los dos `ImageViewerState` del fork (opt-in de la 58; hoy a propósito en false — congelaría el spinner Compose de carga), y `enableTiles/imageLut` quedaron por defecto.

## 22. Feedback del 07/11 (noche) — las barras negras del HQ persisten con la 58

- **Feedback del usuario (07/11, noche):** probada la build `a523759` (bump `ca.mpreg:webgpuviewer` 54 → 58) — **las barras negras del HQ siguen**. El bump no las resolvió.
- **Diagnóstico que se descarta:** el fix upstream de la 58 "última fila de bitmaps de tamaño impar" (plausible causa de barras al pie) **no es** el origen, porque con la 58 aplicada las barras siguen. Tampoco lo son los reworks de cutout ni el sampler de resize de tiles — la 58 los trae y aun así persisten.
- **Diagnóstico que sigue en pie:** con `holdUntilFinal = false` y `enableTiles = false` (defaults actuales del fork, línea 92 de `WebGpuViewer.kt`), el HQ pinta cada frame por la fast-path. Si el origen está en (a) el fondo del lector detrás de la página cuando la página no llena el viewport, o (b) frames negros mientras los tiles se cortan, o (c) el color de clear del surface, son tres vías distintas que conviene separar antes de tocar.
- **Dato fino pendiente del usuario** (recorta las palancas): dónde aparece la barra — (1) al **pie** de la página (gap vertical entre la imagen y el borde inferior), (2) a los **lados** (gap horizontal, vinculado a cutout/aspect ratio), (3) solo en **modo nocturno**, (4) solo al **primer frame** antes de que la imagen se asiente, (5) en **todas** las páginas o solo en algunas (tamaño impar). Cada caso apunta a una palanca distinta.
- **Palancas listas para activar (una por build, en orden de costo):**
  1. `viewerState.holdUntilFinal = true` en `WebGpuViewer.kt:92` (ataca "frames negros mientras los tiles se cortan"; riesgo conocido: puede congelar el spinner Compose de carga — se observa en el teléfono).
  2. `viewerState.enableTiles = true` (enciende el pipeline de tiles de la 58 con pacing y reúso; hoy está off — si las barras NO vienen de tiles, esta no las mueve).
  3. `viewerState.avoidCutout = true` / `alwaysAvoidCutout = true` (si las barras son a los lados en el POCO F6 — punch-hole y esquinas redondeadas).
  4. Volver a verificar `readerBackgroundColor()` en el HQ (no solo en el experimental) — el fix `4d1a9afa1` apuntaba al fondo del lector; confirmar que el HQ pasa por el mismo camino.
- **Estado:** resuelto en §23 — el usuario aportó capturas y el diagnóstico quedó cerrado: no era rendering, era el ajuste Gap del modo tira vertical.

## 23. Ciclo del 07/11 (noche) — diagnóstico y fix de las barras negras: es el Gap del modo tira vertical

- **El usuario aporta capturas comparadas** del mismo capítulo: «donde el vertical continuo las junta entre página y página, el discontinuo coloca esas barras negras en medio». En el modo discontinuo se ven franjas negras horizontales separando página y página DENTRO del mismo capítulo.
- **Causa raíz (verificada en código y en la librería):**
  - El modo «tira vertical» (`CONTINUOUS_VERTICAL`) es el visor continuo con `useGap = true` (`ReadingMode.kt:92/102`).
  - En el HQ (`WebGpuViewer.kt:997-1001`) eso activa `pageGap = continuousGap / 100f` — gap entre TODAS las páginas. En la librería ca.mpreg, `pageGap` es fracción de la altura del viewport (`pageGapPx = pageGap * height`).
  - El ajuste `webgpu_continuous_gap` tenía **default 10** (→ 10 % de la altura de pantalla ≈ 240 px en un POCO F6) y el slider «Gap» de la UI tenía rango `1..100` — **imposible ponerlo a 0**. El gap se pinta con `readerBackgroundColor()` (negro en modo nocturno) → franjas negras siempre visibles.
  - En el experimental (`AndroidxWebGpuContinuousViewer.kt:432-437`) el gap solo existe ENTRE capítulos (fijo, `CHAPTER_GAP_FRACTION`), nunca entre páginas del mismo capítulo — por eso el experimental nunca mostró estas barras.
- **Implicación:** TODO el feedback anterior de «barras negras en el HQ» (§18, §20, §21, §22) era este ajuste, no un bug de rendering. Por eso ni el fix de fondo (`4d1a9afa1`) ni el bump 54→58 las movieron: la librería pintaba exactamente lo que el ajuste le pedía.
- **Fix (`92a71bd0e`), tres cambios:**
  1. `ReaderPreferences.kt` — default de `webgpu_continuous_gap`: 10 → **0** (páginas pegadas, como el modo continuo; quien quiera separación la sube).
  2. `WebGpuViewerSettings.kt` — rango del slider Gap: `1..100` → `0..100` (ahora llega a 0).
  3. `i18n es/strings.xml` — traducciones que faltaban: «Ancho mínimo» y «Separación entre páginas».
- **Nota para el teléfono del usuario:** la preference ya guardada (10) no la toca el nuevo default — con la build nueva hay que bajar «Separación entre páginas» a 0 UNA vez en los ajustes del lector; de ahí en adelante queda fijo.
- **CI:** run `42297312093` **verde** (Build Release APK OK; extensiones omitidas). Entrega: `download/MihonFix-0.21.3-build-92a71bd-arm64.apk` (35,2 MB) — firma v2 `DE:78:…:6A:D0` verificada fuera de CI → **se instala encima sin desinstalar**. Empaquetado verificado (3 dex, `libwebgpu_c_bundled.so` + `libresize.so`, shaders de la 58).
- **Qué probar:** instalar → ajustes del lector → «Separación entre páginas» → 0 → el modo tira vertical debe verse igual al continuo (páginas pegadas, sin franjas negras). El calor del HQ también cabe replantearlo después de este fix: parte de la comparación de §21 pudo estar sesgada por el mismo modo.

## 23-bis. Confirmación del usuario (07/11, noche) — barras negras RESUELTAS

- **El usuario confirma:** «el problema de las barras negras se fue» con la build `92a71bd` (Gap a 0). El bug más longevo del visor queda cerrado: eran el ajuste de separación del modo tira vertical, no un defecto de render.
- **Consecuencia:** las palancas del §22 (`holdUntilFinal`, `enableTiles`, `avoidCutout`) NO hacen falta para las barras — quedan documentadas como referencias futuras. El fix de fondo del lector (`4d1a9afa1`) y el bump 54→58 eran correctos pero no iban a esta causa.
- **Pendiente que queda del visor:**
  - Confirmar la **reanudación** en el experimental continuo (fix `a5237598b`) — el usuario aún no la ha confirmado explícitamente.
  - Replantear la prueba de **calor** del HQ con el gap corregido (la comparación de §21 pudo estar sesgada por las franjas del gap).
  - Limitaciones residuales del experimental (pinch-zoom en tira continua, páginas de transición entre capítulos, saltos de layout, zonas de toque + fling).
  - Zero-copy AHardwareBuffer: parqueado hasta que la librería ca.mpreg entregue AHB desde el decoder.
