# VALIDACIÓN EN DISPOSITIVO — T6 (máquina de estados de página)

**Build:** `MihonFix-0.21.3-build-c2de25f-arm64.apk` · sha256 `1428beb316184ea7e559705471a0f17e52219d71732b8750bcd7b1defda2cf43` · firma estable verificada (cert SHA-256 `DE7889F3…D1296AD0` — instala ENCIMA, sin desinstalar)
**Encargo:** T6a cableado de `ViewerPageStateMachine` · **CI verde:** run 45647679860 (15:39 UTC) en `feat/t6-page-state-machine-wiring` (`c2de25f`, 197 tests) — rama sin mergear a `main` hasta checklist aprobada
**Estado del agente:** **ESPERANDO VALIDACIÓN EN DISPOSITIVO** — el cierre de T6 lo firma el usuario con esta checklist. La CI verde NO sustituye al dispositivo.

> Reglas antes de empezar:
> - **Instalar sin desinstalar** la build anterior (misma firma; si el instalador pide desinstalar, NO continuar y anotarlo).
> - Cada punto: marcar **OK** o **FALLO** y, si hay algo raro, describirlo en la línea de notas. Un FALLO abre ciclo correctivo aparte — no se parchea sobre la marcha.
> - Los puntos 6–8 son los tests de migración del informe de auditoría (sección 4), plegados aquí para hacerlos con el dedo, sin leer código.

---

## 1. Instalación encima de la actual

- [ ] El APK se instala **sin pedir desinstalar** la versión anterior.
- [ ] La app abre y la biblioteca está intacta (mangas, capítulos leídos, fuentes).
- Notas: ____________

## 2. Visor STOCK: paridad total

- [ ] Abrí un manga en modo STOCK (el de siempre) y leí un capítulo entero.
- [ ] El comportamiento es idéntico al de la build anterior: carga de páginas, scroll, transición de capítulo.
- Notas: ____________

## 3. Visor HQ: paridad total

- [ ] Cambié a HQ y leí un capítulo entero.
- [ ] Idéntico a la build anterior: sin pantallas de error nuevas, sin páginas que tarden más de lo normal.
- Notas: ____________

## 4. Visor EXPERIMENTAL (androidx.webgpu): lectura normal + degradación esperada

- [ ] Abrí un capítulo entero en EXPERIMENTAL y se leyó de punta a punta.
- [ ] Zoom con doble toque (fit ↔ 2.5×), swipe para pasar página: igual que antes.
- [ ] Si el visor «bajó solo» a HQ en algún momento, anotar CUÁNDO pasó (es esperado tras 3 fallos de página seguidos — circuit breaker; si no bajó, mejor).
- Notas: ____________

## 5. Reintento por tipo de fallo (T6a) — solo lo provocable en pantalla

- [ ] **Corte de red a mitad de carga:** con una página cargando, activé modo avión → la página muestra el error; desactivé el avión y toqué **Reintentar** → la página carga. (Puede que se reintente sola antes de tocar nada — anotar si pasó.)
- [ ] **Retry manual siempre funciona:** en la misma situación, el botón Reintentar carga la página aunque la carga se haya cortado varias veces seguidas.
- [ ] **Cancelación sin reintento automático:** corté una carga a mano (salir del capítulo y volver rápido, o cancelar desde la notificación de descarga si aplica) → la página **no** se queda reintentando sola en bucle; queda en error o carga normal al re-presentarse.
- Notas: ____________

## 6. Guard remake (M1) — obra homónima sin metadatos

- [ ] En migración, busqué un título que tenga un remake u otra obra con el **mismo nombre exacto** en otra fuente, donde esa fuente no da autor/géneros.
- [ ] La candidata aparece en la lista **para revisión** con la marca de «sin datos» y **NO** está preseleccionada en el lote (el sistema no la migra solo).
- Notas: ____________

## 7. Guard autor coreano (M4) — hangul vs romano

- [ ] Elegí una obra donde una fuente lista el autor en hangul (por ejemplo «김도») y otra en latino («Kim Do»).
- [ ] El veredicto de migración cuenta el autor como **coincidente** (la línea de veredicto incluye Autor) — ya no es falso negativo.
- Notas: ____________

## 8. Doble toque en migración masiva

- [ ] Toqué «migrar» dos veces muy rápido en la migración masiva → una sola corrida, conteos del diálogo coherentes (intentados = candidatos reales, no duplicados).
- Notas: ____________

## 9. Espacio libre

- [ ] Cualquier otra cosa que notaste rara en esta build (aunque parezca menor):
- Notas: ____________

---

**Resultado global:** ☐ todo OK · ☐ hay fallos (listar números de punto) · Fecha de la prueba: ______ · Build probada: sha256 terminación `…da2cf43`

---

# RONDA 2 (fijada) — tras el ciclo correctivo R1–R4

**Build de esta ronda:** `MihonFix-0.21.3-build-362c432-arm64.apk` · sha256 `6b3b1369e108fe35bae28189640320b7d7aa4baeaa0cf9d370f3855781ad0d59` · firma ESTABLE verificada (cert `DE7889F3…D1296AD0`, instala ENCIMA) · CI verde run 45673989574 (18:19 UTC, `362c432`) · **Cambios:** fix del drift +1 (Fallo B) y aclaración del zoom (Fallo A: en modos continuos el visor experimental nunca tuvo zoom — feature de fase futura; en modo paginado sí zoomea).
**Nota:** el fix del drift toca solo el visor experimental CONTINUO. STOCK y HQ no cambiaron; los puntos de migración no se repiten (respaldados por unit tests, decisión del usuario).

## 1. Zoom en EXPERIMENTAL — MODO PAGINADO (L2R / R2L / vertical)

- [ ] Pellizco: zoomea sobre el punto de los dedos, hasta 4×.
- [ ] Doble toque: alterna ajustado ↔ 2.5× sobre el punto tocado.
- [ ] Con página ampliada: arrastrar con un dedo mueve la página (pan).
- Notas: ____________

## 2. Zoom en EXPERIMENTAL — MODO CONTINUO (webtoon / vertical continuo)

- [ ] (Informativo, no es fallo nueva) El zoom NO está disponible en este modo del visor experimental — confirmar que el scroll normal funciona bien.
- Notas: ____________

## 3. Drift: abrir el MISMO capítulo 5 veces seguidas sin moverse

- [ ] En EXPERIMENTAL continuo: anota la página de partida (____) → abre en la MISMA página las 5 veces.
- [ ] En EXPERIMENTAL paginado: ídem (____).
- Notas: ____________

## 4. Regresión rápida

- [ ] Un capítulo en STOCK y uno en HQ: comportamiento idéntico al de siempre.
- [ ] Avión a mitad de carga → página falla → quitar avión → Reintentar carga.
- Notas: ____________

## 5. Notas libres

- [ ] Cualquier otra cosa rara:
- Notas: ____________

**Resultado global:** ☐ todo OK · ☐ hay fallos (números de punto) · Fecha: ______ · Build probada: sha256 terminación `…ad0d59`
