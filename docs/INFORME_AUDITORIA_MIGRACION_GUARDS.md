# MihonFix · Auditoría de la migración asistida con guards (fase B, 2026-11-24)

**Alcance:** auditoría solo lectura del CÓDIGO de la migración asistida con guards — PR #6/#7 (rama `fix/migration-guards`, ya fusionada; commits `4f4b79c..bee9c64`) y los fixes de migración masiva del 15/11 (`3ea881954`, `519c7b4`). No se arregla nada: cada hallazgo queda para un encargo correctivo aparte.

**Código auditado** (pruebas archivo:línea sobre `main` en `cd49eb4`):
- `app/src/main/java/mihon/feature/migration/repair/MetadataMatcher.kt` (629 líneas)
- `app/src/main/java/mihon/feature/migration/repair/TitleMatcher.kt` (159)
- `app/src/main/java/mihon/feature/migration/repair/Romanizer.kt` (176)
- `app/src/main/java/mihon/feature/migration/list/search/SmartSourceSearchEngine.kt` (130)
- `app/src/main/java/mihon/feature/migration/list/MigrationListViewModel.kt` (685)
- `app/src/main/java/mihon/feature/migration/list/MigrationListScreenContent.kt` (veredicto visible)
- `app/src/main/java/eu/kanade/domain/source/service/SourcePreferences.kt` (defaults de guards)

---

## 1. Tabla de verificación G1–G7 (afirmación → ¿verificada?)

| # | Afirmación (docs) | ¿Verificada? | Prueba en el código |
|---|---|---|---|
| G1 | Validación de metadatos 2-de-4 (autor/estado/descripción/géneros), veredicto visible | **SÍ** | `MetadataMatcher.validate()` evalúa las 4 señales y exige `matched.size >= MIN_MATCHED_SIGNALS` con `MIN_MATCHED_SIGNALS = 2` (MetadataMatcher.kt:222, 449–491); señales sin datos a ambos lados no se evalúan (455, 461, 468, 479). Veredicto visible en la lista: `MetadataVerdictLine` muestra «Matches: …» o la marca «sin datos» (MigrationListScreenContent.kt:311–312, 326–345). |
| G2 | Criterio de capítulos: ratio ≥ 0,5; retrasos grandes marcados/rechazados | **SÍ** | `validateChapters`: ratio `candidate/original`, `< CHAPTER_REJECT_RATIO (0.5)` → `Rejected` (MetadataMatcher.kt:528, 565–573); gap absoluto > 25 caps → `NotViable` (545, 570); 6–25 caps detrás → aceptado **con aviso** «Va N caps atrás» (534, 571–572; UI en MigrationListScreenContent.kt:346–352). |
| G3 | Veto por autor | **NO** (contradicha por el código) | El autor es **una señal más, explícitamente NO veto**: «callers treat AUTHOR as ONE signal among four (no veto)» (MetadataMatcher.kt:239–241, 454–457) y el comentario en el gate de identidad: «author as a signal (not a veto — phase 2)» (MigrationListViewModel.kt:346). Lo único parecido a un veto es `AUTHOR_MISMATCH` en `TitleMatcher.findMatch`, pero en el flujo de migración masiva se llama **sin** autor/géneros (SmartSourceSearchEngine.kt:109 — solo 3 argumentos), así que la compatibilidad es siempre cierta (TitleMatcher.kt:59–62). Ver hallazgo M3. |
| G4 | Romanizador hangul/kana para títulos **y autores** | **PARCIAL** | Títulos: SÍ — `titleLevel` romaniza ambos lados (MetadataMatcher.kt:411–413) y `deepSearch` añade la consulta romanizada (SmartSourceSearchEngine.kt:71–74); `Romanizer.romanize` implementa RR hangul con apellidos convencionales + Hepburn kana (Romanizer.kt:125–167). **Autores: NO** — `MetadataMatcher.normalizeAuthors` solo normaliza con `TitleMatcher.normalize` (MetadataMatcher.kt:137–143), sin pasar por `Romanizer`: un autor «김도» en una fuente y «Kim Doe» en otra **nunca casan**. Ver hallazgo H2. |
| G5 | Identidad vs viabilidad con UNA petición por candidato | **SÍ (con matiz)** | Gates separados: GATE A identidad (`isSameWork`, MigrationListViewModel.kt:348–371) y GATE B viabilidad (`validateChapters`, 383–408). Una petición por candidato elegido: `getMangaUpdate(fetchDetails=true, fetchChapters=true)` única (MigrationListViewModel.kt:316–334, comentario «ONE getMangaUpdate per pick»). Matiz: en modo deep search hay consultas de búsqueda extra **por fuente** (la romanizada, SmartSourceSearchEngine.kt:71–74) — por candidato elegido sigue siendo un fetch. |
| G6 | Anclas distintivas + viabilidad dura | **SÍ** | `narrativeOverlap` con `MIN_ANCHOR_LENGTH=4`, exclusión de vocabulario genérico y de subgénero (`GENERIC_WORDS`, `SUBGENRE_WORDS`), sin prefijos crudos — solo sufijo de partícula coreana (MetadataMatcher.kt:254–342, 344–356); umbral `MIN_NARRATIVE_ANCHORS=4` (364). Viabilidad dura: `ChapterVerdict.NotViable` deja de ofrecer la fuente como destino (515, 570; MigrationListViewModel.kt:396–405). |
| G7 | Fixes 15/11: fallos no se disfrazan de éxitos; calidad del match gana a novedad; exactas entre fuentes; conteos sincronizados / doble toque | **SÍ** | Fallos visibles: errores de búsqueda → `SearchResult.Error` reintentable, nunca `NotFound` (MigrationListViewModel.kt:262, 163–170); fallo de migración masiva deja la fila en Error y el diálogo usa `intentados` (530–543); `migrateNow` falla con toast (576–583). Calidad > novedad: `compareBy(isExactTitle, similarity, latestChapter)` (203–213) y el secuencial no corta en el primer difuso si puede haber exacto (214–235). Exactas entre fuentes: centinela `currentUrl=""` mantiene la pasada exacta cross-source (304–314, 230). Doble toque: guard de reentrada `migrateJob?.isActive` + diálogo síncrono (484–490); `removeManga` atómico dentro de `state.update` y cancela el scope (603–611). |

**Resumen:** 5 verificadas (G1, G2, G5, G6, G7), 1 parcial (G4 — autores sin romanizar), 1 contradicha (G3 — el veto por autor ya no existe; fue retirado en fase 2 y los docs no lo reflejan).

---

## 2. Hallazgos por severidad (no se arreglan en este encargo)

### Alto

**A-H1 · El auto-aceptar del título exacto ignora autor/géneros y puede migrar la obra equivocada sin preguntar.**
- **Dónde:** SmartSourceSearchEngine.kt:107–113 (`pickHybridBest` → `TitleMatcher.findMatch(title, currentUrl, candidates)` sin pasar `currentAuthor`/`currentGenres`), TitleMatcher.kt:59–62 (autor/género vacíos = siempre compatible), 148 (AutoAccept), MigrationListViewModel.kt:507–509 (`isExactTitle` → `isPreselected` → migra en el lote sin revisión del usuario).
- **Qué es:** en el flujo de migración masiva, un candidato con el mismo título normalizado y URL distinta se marca exacto y preseleccionado **sin que autor ni géneros participen en esa decisión**. La puerta de identidad posterior lo puede salvar si la fuente entrega metadatos que contradigan (Rejected con ninguna señal a favor → rechazado); pero si la fuente extrae poco o nada de metadatos, el veredicto cae en `NoData` y el título fuerte (EXACT) basta para identificarlo (MetadataMatcher.kt:626) — el fallback por preferencia (`migrationMetadataFallbackToTitle`, default `false`) ni siquiera se consulta para títulos fuertes.
- **Consecuencia práctica:** remakes, ediciones u obras distintas que comparten título exacto en otra fuente («Solo Leveling» original vs una parodia con el mismo nombre) se auto-migran en el lote y **no lo sabrías**: el veredicto se muestra como «sin datos», no como rechazo.
- **Mitigación parcial existente:** el gate de capítulos rechaza espejos con menos de la mitad de capítulos; pero un remake al día pasa ambos gates.

**A-H2 · `authorsMatch` por contención acepta apellidos compartidos y, sumado a estado, puede validar la obra equivocada.**
- **Dónde:** MetadataMatcher.kt:242–248 (contención de nombres completos sin longitud mínima: `name.contains(it)`), combinada con el gate de identidad: `Accepted` con autor+estado hace `weakSignalsOnly=false` (615–618) → un título FUZZY se acepta con solo esas dos señales.
- **Qué es:** «Park Joo» vs «Park» casan por contención (intención: créditos de estudio), pero también casan dos autores humanos distintos que comparten apellido. Con `Accepted{AUTHOR, STATUS}` la guard de señales débiles (que cubre solo estado+géneros) no aplica.
- **Consecuencia práctica:** dos obras del mismo autor (o del mismo apellido mal normalizado) con títulos parecidos (FUZZY ≥ 0,6) se aceptan con 2 señales y se ofrecen como migración — el escenario exacto que la guard de fase 3 quiso cerrar para estado+géneros queda abierto para autor+estado.

### Medio

**A-M1 · Los autores no se romanizan (afirmación G4 parcial): falso negativo sistemático en obras coreanas.**
- **Dónde:** MetadataMatcher.kt:137–143 (`normalizeAuthors` sin `Romanizer`), frente al título que sí romaniza (411–413).
- **Qué es:** si una fuente lista el autor en hangul («김준직») y la otra en latino («Kim Jun-jik»), la señal AUTHOR se evalúa y **discrepa**. Con título FUZZY esto convierte un match correcto en `Rejected` sin señales a favor → candidato correcto rechazado silenciosamente (solo queda el log INFO, MigrationListViewModel.kt:365–370).
- **Consecuencia práctica:** no se pierden datos — lo que pasa es que la migración correcta no se ofrece y el usuario concluye «no hay alternativas». Es lo inverso del riesgo de datos, pero degrada la función justo en el caso que Romanizer construyó para resolver.

**A-M2 · La señal de géneros es muy barata para fuentes con pocos géneros.**
- **Dónde:** MetadataMatcher.kt:479–484 — la intersección se compara contra la **mitad del conjunto más chico**: un candidato con 2 géneros que coinciden casan aunque la obra local tenga 12.
- **Qué es:** la combinación géneros+estado genera `Accepted` solo si acompaña a autor o descripción (guard de débiles, 615–618), pero géneros+descripción también: un sinopsis con 4 anclas compartidas de subgénero que sobrevivió a la lista negra más 2 géneros casan sin autor ni título fuerte.
- **Consecuencia práctica:** candidata de otro título con la misma sinopsis boilerplate (subgénero regresión, torre, romance de campus) puede llegar a aceptada si los anclas de los sinopsis coinciden por casualidad; el veredicto se muestra, pero el usuario rara vez lo audita.

**A-M3 · «Veto por autor» afirmado en docs pero retirado del código (G3).**
- **Dónde:** MetadataMatcher.kt:239–241; MigrationListViewModel.kt:346; SmartSourceSearchEngine.kt:109.
- **Qué es:** discrepancy documental: los docs describen un veto que la fase 2 convirtió en señal no vetante. El código está en lo correcto (el veto rompía romanizaciones y créditos de estudio); los docs quedaron atrás.
- **Consecuencia práctica:** quien audite por los docs creerá que existe una protección que no está. Corrección documental, sin cambio de comportamiento.

### Bajo

**A-B1 · `anchorsMatch` puede validar anclas falsas con sufijos cortos de partícula.**
- **Dónde:** MetadataMatcher.kt:305–308, 332–342 — la lista de sufijos incluye fragmentos ambiguos («ga», «ro», «wa», «man»): «sabor» − «ro» = «sab» casaría con un token «sab» de ≥4 letras en el otro lado.
- **Consecuencia práctica:** ruido marginal en la señal de descripción; la exigencia de 4 anclas (364) lo amortigua. Vigilar si aparecen falsos positivos de descripción en pruebas en dispositivo.

**A-B2 · Deep search añade una consulta extra por fuente cuando el título es romanizable.**
- **Dónde:** SmartSourceSearchEngine.kt:63–74. «UNA petición por candidato» se cumple para el fetch de detalles/capítulos (MigrationListViewModel.kt:316–334), pero el costo de red por fuente crece en modo deep search. Documentado en el código; solo conviene tenerlo presente al medir lentitud.

**A-B3 · `Skip` del gate de capítulos + `NoData` + título fuerte = aceptación con evidencia mínima.**
- **Dónde:** MetadataMatcher.kt:566 (conteo desconocido no juzga), 626 (título fuerte identifica solo), MigrationListViewModel.kt:407 (`Skip` → sin aviso de capítulos).
- **Qué es:** fuente que no lista capítulos ni extrae metadatos, con título exacto: pasa ambos gates «sin opiniones». El veredicto «sin datos» sí se muestra en la UI (MigrationListScreenContent.kt:332–334), que es lo mejor que se puede hacer sin datos.
- **Consecuencia práctica:** una fuente espejo en blanco igual aparece como destino válida; el usuario ve la marca «sin datos» y decide.

---

## 3. Riesgos de datos del usuario (migración = alto impacto)

1. **Falso positivo silencioso — el peor caso:** A-H1. Título exacto + fuente con metadatos vacíos = `NoData` + identidad por título fuerte + preselección en el lote. La migración **copia y reemplaza** los metadatos y capítulos de la entrada local (MigrateMangaUseCase): si el candidato es otra obra, el usuario pierde el progreso de lectura asociado a la entrada original y no recibe ningún aviso (el veredicto mostrado es «sin datos», no una advertencia). Es el único camino encontrado donde la migración equivocada ocurre sin ninguna señal en contra.
2. **Matches idénticos que migran la obra equivocada:** el caso general de A-H1 (remakes/parodias con el mismo título) y el camino FUZZY de A-H2 (autor compartido + estado). En ambos la UI muestra el veredicto («Matches: Author, Status» / «sin datos»), pero solo el usuario que lee esa línea fina se protege.
3. **Estados que se borran sin haber migrado:** NO encontrado. El cleanup post-lote borra solo las entradas que realmente migraron; las fallidas pasan a `SearchResult.Error` reintentable y el diálogo reporta `intentados`, no el total (MigrationListViewModel.kt:530–543). `hideUnmatched` oculta filas de la lista pero no borra datos. `removeManga` es atómico y cancela el scope de la fila (603–611), sin ventana de carrera detectada.

---

## 4. En criollo

**¿Qué hace esta función?** Cuando migrás un manga a otra fuente, el sistema no se fija solo en el título: le pide a la fuente candidata los detalles y los capítulos (una sola consulta por candidato), compara 4 cosas — autor, estado, sinopsis y géneros — y exige que al menos 2 coincidan; si el título es muy parecido pero no idéntico, exige más pruebas todavía; y si la fuente candidata tiene menos de la mitad de los capítulos que ya tenés, o más de 25 capítulos de retraso, ni te la ofrece porque sería un espejo viejo. Además romaniza el coreano y el japonés para comparar títulos entre escrituras.

**¿Qué tan confiable es?** Para la identidad de la obra: alta — después de las falsas positivas reales de la fase 3, la puerta exige evidencia fuerte y las diez migraciones validadas en dispositivo pasaron por título exacto o romanizado. Para la viabilidad: alta — el criterio de capítulos es el más honesto de todo el flujo. Los dos agujeros que quedan están en los bordes: el título-exacto-obra-distinta-sin-metadatos (A-H1) y el autor compartido (A-H2), que son exactamente los casos donde la evidencia disponible es poca o engañosa.

**3 preguntas que deberías poder responder con el móvil para validarla de verdad:**
1. **El caso remake:** migrá una obra que tiene un remake o parodia con el **mismo título exacto** en otra fuente. ¿El candidato elegido es el correcto o el lote se lo lleva sin preguntar? ¿Qué dice la línea de veredicto («Matches…» / «sin datos») de la fila?
2. **El caso coreano:** elegí una obra donde una fuente pone el autor en hangul y otra en latino. ¿Aparece la fuente correcta como candidata, o desaparece de la lista (falso negativo del hallazgo A-M1)?
3. **El caso espejo viejo:** buscá una obra donde una fuente esté 30+ capítulos atrás. ¿La lista la excluye sola (viabilidad dura) o la muestra marcada? Y una fuente con la mitad de capítulos exactos: ¿queda fuera?
