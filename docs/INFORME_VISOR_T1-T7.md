# Informe final — refactor del visor del lector (T1–T7)

**Rama:** `fix/viewer-refactor-t1-t7` (7 commits, uno por tarea, sobre `main`)
**Fecha:** 2026-11-24 · **Autor:** sesión del agente · **Estado:** pendiente CI verde + confirmación del usuario

## Resumen técnico

La orden de trabajo pedía separar las tres decisiones que estaban mezcladas en el lector — modelo de navegación (paginado L2R/R2L/vertical vs continuo), backend de render (stock / HQ ca.mpreg / experimental androidx.webgpu) y política de degradación — y corregir los cuatro defectos de la auditoría previa (D1–D4), con un commit por tarea y tests JVM.

| Tarea | Commit | Contenido |
|---|---|---|
| T1 (D1) | `3630ace` | Degradación segura ante preferencia corrupta: se elimina el `IllegalStateException` de las ramas `DEFAULT` de `toViewer`; un valor inválido degrada al visor stock L2R con logcat. Nueva capa pura `ReadingModePolicy` + tests fuzz JVM. |
| T2 (D2) | `16cb79a` | Migración one-shot: `WebGpuViewerModeMigration.migrateOnce` calcula el modo desde las claves legacy UNA vez y lo escribe; después las claves viejas quedan en solo-lectura. Valor corrupto → STOCK, nunca se reescribe. KDoc anota la retirada futura de claves. |
| T3 (D3+D4) | `ac1380e` | KDoc de `AndroidxWebGpuViewer` refrescado (hablaba del flag antiguo) y ramas `when` duplicadas colapsadas (refactor puro). |
| T4 | `982de30` | Capa de gestos compartida `ViewerGesturePolicy`: gate de long-press, toggle de doble tap, clamp de zoom, pan anclado al centroide y acumulador de swipe (eje, RTL, umbral 18 %). El visor experimental la consume con comportamiento idéntico. |
| T5 | `29a0bf6` | Política de backend + circuit breaker `ViewerBackendPolicy`: escalera EXPERIMENTAL→HQ→STOCK, breaker de 3 fallos de página consecutivos (un acierto resetea), degradación por sesión que JAMÁS escribe la preferencia; re-sondeo al arrancar de nuevo. Cableado en `AndroidxWebGpuBaseViewer` y `ReaderActivity`. |
| T6 | `9fb38d0` | Máquina de estados de página `ViewerPageStateMachine`: Queued→Loading→Ready→Failed(reason) con clasificador de fallo (HTTP/NETWORK/DECODE/STORAGE/CANCELLED/UNKNOWN) y reintento por tipo (transitorios → loader, máx. 2 intentos; decode/storage → re-upload; cancel → sin reintento automático). Sin rutas de crash. |
| T7 | `e417756` | Matriz de verdad de 36 casos (3 modos × 6 lecturas × 2 estados de fallback) sobre `selectBackend`, más tests de semántica de flags y de migración. |

**Verificación local:** el sandbox no tiene Android SDK ni Gradle (limitación documentada en `AGENTS.md` del repo), así que las políticas puras se verificaron con un harness JVM (kotlinc 2.0.21 + runner propio, fuera del repo): **17.017 aserciones, 0 fallos**. Los mismos tests están en formato JUnit en `app/src/test/` para la CI. La CI de GitHub («Build APK» + «Build & Test») es la que valida compilación, spotless y el conjunto completo sobre cada commit.

**Lo que NO cambió:** nada del comportamiento para usuarios STOCK/HQ; la semántica de fallback (EXPERIMENTAL degrada a HQ por sesión) se conserva; ninguna preferencia del usuario se escribe desde la degradación; no se tocó applicationId, firma ni versionCode; no hubo force push ni rebase.

**Pendientes:**
- Confirmación del usuario tras instalar la build que salga de esta rama.
- Cableado completo de `ViewerPageStateMachine` en los visores (la máquina y su política están listas y testeadas; el enrutado actual de `retryPageLoad` ya coincide con la política).
- Retirada de las claves legacy (anotada como TODO en `WebGpuViewerModeMigration.kt`) cuando no queden builds anteriores a la migración one-shot en circulación.

---

## En criollo (para el usuario, sin tecnicismos)

**¿Qué se hizo?** Se arreglaron cuatro problemas del lector y se dejó la base lista para que el visor nuevo sea mucho más estable:

1. **Ya no se cuelga el lector por una preferencia dañada.** Antes, si el ajuste del modo de lectura quedaba corrupto (por ejemplo tras un corte de luz o una actualización a medias), abrir el lector lo tiraba abajo. Ahora, ante cualquier valor raro, el lector arranca con el visor clásico y anota en el log qué pasó. Peor caso: leés igual.

2. **El ajuste nuevo manda de verdad.** Antes, cada vez que abrías la app, el sistema recalculaba tu modo de visor mirando los interruptores viejos, así que si algo viejo quedaba en «sí», seguía mandando para siempre. Ahora: la primera vez que abrís la app después de actualizar, se guarda el modo una sola vez y desde ahí el ajuste nuevo es el único que cuenta. Tus interruptores viejos ya no molestan.

3. **El visor experimental se cuida solo.** Si el visor experimental falla tres páginas seguidas, el lector solo baja a la versión HQ sin que hagas nada; si la HQ también fallara, baja a la clásica. Ojo: **solo por esa sesión** — al día siguiente vuelve a intentar el experimental por su cuenta, y tu preferencia guardada jamás se pisa. Un fallo aislado (una imagen rota de algún capítulo) no provoca la bajada.

4. **Los gestos quedaron unificados.** El toque para avanzar/retroceder, la presión larga para el diálogo de página, el doble toque para zoom y el deslizar para pasar página ahora siguen un mismo «manual» para todos los visores, así que el visor nuevo se comporta igual que el clásico y los futuros van a heredar todo.

5. **Manejo de página a prueba de todo.** Cada página pasa por estados claros (en cola → cargando → lista → falló) y el reintento depende del tipo de problema: si es un problema pasajero del servidor lo intenta de nuevo un par de veces; si ya bajó la imagen y falló al dibujarla, la vuelve a dibujar; y si cancelaste, no insiste solo. Nada de esto puede tirar la app abajo.

**¿Por qué?** Porque un lector de manga se usa una hora seguida y el peor escenario es que se cierre solo a mitad del capítulo 12 de 40. Con esto, ni un ajuste dañado ni un servidor caprichoso te sacan de la lectura: lo peor que puede pasar es que leas con el visor clásico hasta la próxima sesión.

**¿Qué hay que hacer?** Instalar la build nueva cuando la CI la entregue (se instala encima de la actual, sin desinstalar) y leer con normalidad: modos stock y HQ se ven exactamente igual que antes. Si usás el visor experimental y notás que «bajó solo» a HQ, avisá — es el mecanismo nuevo cumpliendo su función, pero conviene confirmarlo.
