# VALIDACIÓN EN DISPOSITIVO — T6 (máquina de estados de página)

**Build:** `MihonFix-0.21.3-build-f520f77-arm64.apk` · sha256 `f5c290fa1801042aa14df6828de06ccffe313b19f42a60be06e3fd417ba5669f` · firma estable verificada (cert SHA-256 `DE7889F3…D1296AD0` — instala ENCIMA, sin desinstalar)
**Encargo:** T6a cableado de `ViewerPageStateMachine` · **CI verde:** run de 05:10 UTC en `feat/reader-page-state-wiring` (`f520f77`, 197 tests)
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

**Resultado global:** ☐ todo OK · ☐ hay fallos (listar números de punto) · Fecha de la prueba: ______ · Build probada: sha256 terminación `…5669f`
