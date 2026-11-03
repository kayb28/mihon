# Traspaso de estado — rama `feature/webgpu-clean`

- Repositorio: `kayb28/mihon` (fork WebGPU de Mihon)
- Rama: `feature/webgpu-clean`
- Fecha del traspaso: 03/11/2026, 11:11 (hora de São Paulo)
- Regla de oro de este traspaso: solo se marca «verificado» lo que pasó por CI verde o se probó en el teléfono del usuario. Revisar código no cuenta como verificado.

## 1. Estado de la rama

- **HEAD al generar este documento:** `65a8c653c95cf79477abf92411f2f1f6880d71c7` (`65a8c65`).
- El commit que añade este documento se coloca justo encima de ese HEAD (no puede contener su propio hash).
- Sincronía: local y `origin/feature/webgpu-clean` estaban en el mismo hash al generar el documento (verificado con `git ls-remote`).
- Árbol de trabajo limpio (sin cambios pendientes de commitear).

Últimos commits, de más reciente a más antiguo (fechas en UTC):

| Hash | Fecha (UTC) | Mensaje |
|---|---|---|
| `65a8c65` | 2026-11-03 13:24 | fix: mandar coincidencias aproximadas del reparador a revision manual |
| `702f10f` | 2026-11-03 13:22 | fix: reconocer kanji, hangul y cirilico en TitleMatcher y BaseSmartSearchEngine |
| `be84517` | 2026-11-03 03:01 | fix: invertir direccion de arrastre en tira androidx (reverseDirection = true) |
| `fffacf1` | 2026-11-03 01:54 | fix: exponer settingsViewModel como protected para la subclase de tira |
| `1ce0d9c` | 2026-11-03 01:52 | fix: el toque en tira avanza 3/4 de pantalla como el stock |
| `33f5b2c` | 2026-11-02 23:44 | fix: forzar Float en la viewport de maxScrollPx (la cadena ?: mezclaba Int y Float) |
| `7d1f675` | 2026-11-02 23:37 | fix: el visor androidx deja de medir la tira completa y oculta el menú como el stock |
| `436811a` | 2026-11-02 22:58 | chore: sondas ① y ② solo en logcat, menos toasts por toque |
| `57ce07d` | 2026-11-02 22:58 | fix: los ajustes stock del lector vuelven al diálogo con el renderizador WebGPU activo |
| `051bb53` | 2026-11-02 22:01 | fix: el visor WebGPU de tira lee las zonas de toque de webtoon, como el stock |
| `bba3a3c` | 2026-11-02 21:26 | fix: eliminar parámetro duplicado onClickTrustAll tras el merge (ExtensionsTab) |
| `1d0893c` | 2026-11-02 21:20 | merge: traer a feature/webgpu-clean las correcciones del reparador de enlaces (main) |

## 2. Aprobado pero NO aplicado

**A la fecha de este documento no queda nada en este estado.**

El «parche de títulos Unicode + coincidencias aproximadas a revisión» (aprobado por el usuario) **ya está aplicado, empujado y compilado en CI verde** — ver sección 3. Se registran aquí, por trazabilidad, los dos commits de ese parche y los archivos que tocan:

| Commit | Mensaje | Archivos que toca |
|---|---|---|
| `702f10f` | fix: reconocer kanji, hangul y cirilico en TitleMatcher y BaseSmartSearchEngine | `app/src/main/java/mihon/feature/migration/repair/TitleMatcher.kt` · `app/src/main/java/mihon/feature/migration/list/search/BaseSmartSearchEngine.kt` · `app/src/test/java/mihon/feature/migration/repair/TitleMatcherTest.kt` (nuevo; 11 casos JUnit 5 — no corren en CI, el workflow solo compila) |
| `65a8c65` | fix: mandar coincidencias aproximadas del reparador a revision manual | `app/src/main/java/mihon/feature/migration/repair/RepairLinksViewModel.kt` |

Qué hace cada uno:

- `702f10f`: los patrones de normalización Unicode dejan de tratar kanji, hangul y cirílico como símbolos que se borran (el patrón anterior usaba `\p{Alnum}`, que solo cubre ASCII; con un título guardado en CJK la limpieza dejaba una cadena vacía, y dos cadenas vacías comparadas daban un 100 % falso).
- `65a8c65`: en el reparador de enlaces, las coincidencias aproximadas ya no pasan solas a «coincidencia encontrada»: van a revisión manual. Solo los aciertos exactos pasan automáticos (incluido el re-chequeo de título exacto que hace la búsqueda profunda).

## 3. Aplicado y compilado en CI

Estado actual: **el HEAD `65a8c65` compila verde en CI (Run #82, 4 jobs, todos success)**. Lista de cambios recientes con el run que los compiló:

| Cambio | Commit | Run de CI | Resultado |
|---|---|---|---|
| Parche de títulos: aproximadas a revisión manual | `65a8c65` | #82 | success |
| Parche de títulos: Unicode (kanji/hangul/cirílico) | `702f10f` | sin run propio | cubierto acumuladamente por #82 (ambos commits se empujaron juntos) |
| Invertir arrastre en tira androidx | `be84517` | #81 | success |
| settingsViewModel protected para la subclase de tira | `fffacf1` | #80 | success |
| Toque en tira avanza 3/4 de pantalla | `1ce0d9c` | #79 | cancelled (anulado al empujar `fffacf1` dos minutos después; el estado quedó cubierto por #80) |
| Float en la viewport de maxScrollPx | `33f5b2c` | #78 | success |
| Visor androidx: medición de tira y menú | `7d1f675` | #77 | **failure** (compilación rota; corregida por `33f5b2c` en #78) |
| Sondas ① y ② solo en logcat | `436811a` | #76 | success |
| Ajustes stock del lector con renderizador WebGPU activo | `57ce07d` | sin run propio | cubierto acumuladamente por #76 (ambos commits se empujaron juntos) |
| Zonas de toque de webtoon en el visor WebGPU de tira | `051bb53` | #75 | success |
| onClickTrustAll duplicado tras el merge | `bba3a3c` | #74 | success |
| Merge de correcciones del reparador | `1d0893c` | #73 | **failure** (compilación rota; corregida por `bba3a3c` en #74) |

## 4. Confirmado por el usuario en su teléfono

- En tira vertical, deslizar hacia arriba sube la página y hacia abajo la regresa (verificado en ambos modos).
- El menú del lector abre y se cierra.
- Las zonas de toque funcionan en modo «Por páginas».

## 5. Pendiente de confirmar en teléfono

- El crash de 274.869 px en el visor androidx: falta reproducirlo en el teléfono para confirmar si sigue ocurriendo con el build actual.

## 6. Pendientes de código, en orden

1. Escala «Inteligente» en tira.
2. Grises e invertidos sin conectar al visor WebGPU.
3. Espacio lateral en tira sin aplicar.
4. `MigrateMangaUseCase` sale sin error si la fuente destino no está instalada.
5. La migración no es atómica (varias escrituras separadas).
6. Un fallo de red en el reparador se muestra como «sin coincidencia».
7. Pendiente de respuesta: si la migración masiva preselecciona coincidencias aproximadas. *(Revisión de código del 03/11/2026 — no es prueba en teléfono: la migración masiva usa todo resultado «Success», exacto o aproximado, sin filtrar; la única protección visual es la insignia con el porcentaje. Ver `migrateMangas()` en `MigrationListViewModel.kt`. Falta la decisión del usuario sobre si se cambia.)*

## 7. Lo que NO se debe hacer sin confirmación del usuario

- Revertir commits.
- Cambiar la pantalla de ajustes.
- Tocar el modo por páginas.
- Regla general de la rama: no aplicar ni empujar ningún cambio que no esté aprobado explícitamente (un commit por cambio aprobado, para poder revertirlos por separado).

## 8. Pruebas pendientes del usuario (pasos concretos)

Repetir la migración con los 9 mangas de la prueba anterior —entre ellos ZomGan, Zui Qiang Shen y Zettai ni Damage wo…— y revisar el porcentaje de cada pareja.

Pasos:

1. Usa en el teléfono un APK compilado a partir de `65a8c65` o posterior (por ejemplo, el artefacto del Run #82): es el primer build con el parche de títulos completo.
2. Abre el reparador de enlaces / migración igual que en la prueba anterior y lanza la búsqueda de los 9 mangas.
3. Anota el porcentaje y el estado que muestra cada pareja origen → destino.
4. Resultados de referencia (simulación local en PC/JVM, **no verificados en teléfono**):
   - ZomGan → LectorXD: ~100 % (coincidencia real; debe seguir alta).
   - Zui Qiang Shen → Feng Shen Ji (Kumanga): ~0 % tras el parche (antes mostraba un 100 % falso).
   - Zettai ni Damage wo… → Level Drain (Emperor Scan): ~4,5 % tras el parche (antes mostraba un 100 % falso).
   - Umbral de la migración masiva: 40 %.
5. Criterio de éxito: las dos parejas falsas dejan de ofrecerse como coincidencia automática (quedan en revisión manual o «sin coincidencia»), y las parejas verdaderas, como ZomGan → LectorXD, siguen en 100 %.
6. Si alguna pareja vuelve a mostrar un 100 % sospechoso, anota el título exacto guardado en la biblioteca (copia de seguridad JSON o pantalla de información de la entrada) para poder depurarlo.

---

Nota de seguridad: este documento se revisó antes de guardarse; no contiene tokens, claves, contraseñas ni URLs con credenciales.
