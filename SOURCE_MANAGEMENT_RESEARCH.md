# Hallazgos de auditoría (solo fuentes externas consultadas)

Fecha de consulta: 2026-10-29. Referencia TachiyomiSY `master` en `c1eb1e3cbfd7d5ab0156a75642222c59c764b9c9`. Base Mihon `fix-0.21.3` en `60e1288c170b0f15ed817f3f129d7f9a3bf17884`.

## TachiyomiSY: agrupación por fuente
- Modelo `LibraryGroup`: https://github.com/kayb28/TachiyomiSY/blob/c1eb1e3cbfd7d5ab0156a75642222c59c764b9c9/domain/src/main/java/tachiyomi/domain/library/model/LibraryGroup.kt — constantes `BY_DEFAULT`, `BY_SOURCE` y otros modos SY.
- Preferencia `groupLibraryBy`: https://github.com/kayb28/TachiyomiSY/blob/c1eb1e3cbfd7d5ab0156a75642222c59c764b9c9/domain/src/main/java/tachiyomi/domain/library/service/LibraryPreferences.kt#L246-L254 — key `group_library_by`.
- Cómputo en memoria y grupos por fuente: https://github.com/kayb28/TachiyomiSY/blob/c1eb1e3cbfd7d5ab0156a75642222c59c764b9c9/app/src/main/java/eu/kanade/tachiyomi/ui/library/LibraryViewModel.kt#L202-L289 y https://github.com/kayb28/TachiyomiSY/blob/c1eb1e3cbfd7d5ab0156a75642222c59c764b9c9/app/src/main/java/eu/kanade/tachiyomi/ui/library/LibraryViewModel.kt#L1217-L1269. Agrupa la lista ya cargada mediante IDs y `SourceManager`, no cambia SQL.
- Selector: https://github.com/kayb28/TachiyomiSY/blob/c1eb1e3cbfd7d5ab0156a75642222c59c764b9c9/app/src/main/java/eu/kanade/presentation/library/LibrarySettingsDialog.kt#L364-L427.
- Actualización desde grupos: https://github.com/kayb28/TachiyomiSY/blob/c1eb1e3cbfd7d5ab0156a75642222c59c764b9c9/app/src/main/java/eu/kanade/tachiyomi/ui/library/LibraryTab.kt#L114-L126 y worker `app/src/main/java/eu/kanade/tachiyomi/data/library/LibraryUpdateWorker.kt`.
- SY usa textos/iconos propios para seguimiento y grupos adicionales, pero `BY_SOURCE` usa el recurso común `MR.strings.label_sources`; la fuente local reutiliza el label común.

## Mihon v0.21.3: APIs confirmadas
- `SourcePreferences.kt` ya persiste fuentes habilitadas/ocultas, fuentes fijadas, `migrationSources: Preference<List<Long>>`, flags y deep-search.
- `SourceManager` expone `get`, `getOrStub`, `getAll`, `getOnlineSources`; `getOrStub` es suspend.
- `Source.getPopularManga(page)`, `getLatestUpdates(page)`, `getSearchManga(page, query, filters)` y `getFilterList()` existen en `source-api/src/main/kotlin/eu/kanade/tachiyomi/source/Source.kt`.
- `SManga.genre` / `getGenres()` existen, pero su presencia/significado dependen de la fuente; no permiten inferir contenido adulto de forma genérica fiable.
- El worker actual recibe un ID de categoría (`KEY_CATEGORY`) y filtra por `LibraryManga.categories`; para actualización por fuente hace falta un argumento distinto.
- `ContentWarning` (`SAFE/MIXED/NSFW`) ya existe para advertencias de extensiones; no representa clasificación personal de fuentes y no debe reutilizarse.


## Implementación en `feature/source-management`

La rama de trabajo se creó sobre `fix-0.21.3` (base registrada: `60e1288c170b0f15ed817f3f129d7f9a3bf17884`); el cambio se mantiene fuera de `main`. Se reutilizan preferencias `StringSet` existentes y el `SourceManager`; no se añadió base de datos ni dependencia.

- Clasificación personal por ID: `SourceClassification`, `SourceClassificationEntry` y `SourcePreferences`; el valor sin asignar es `MIXED`, nunca `GENERAL`. El filtro del catálogo conserva idioma y habilitación; el control de etiqueta no llama a los toggles de fuentes.
- Migración: `migrationAllowedClassifications` habilita las tres categorías por defecto. `eligibleMigrationSourceIds` centraliza el filtro antes de búsqueda; se consume tanto por `MigrateSearchViewModel` como por `MigrationListViewModel`/`SmartSourceSearchEngine`. La hoja bloquea el inicio cuando no queda ninguna fuente y la lista muestra el motivo.
- Actualización de biblioteca por fuente: el menú contextual de una fuente inicia el mismo `LibraryUpdateWorker`, transmite `sourceId` aparte de `categoryId` y filtra por `manga.source`; los flujos global y por categoría siguen usando su selector previo.
- Diagnóstico: el menú permite una comprobación manual (sin sondeos automáticos) y muestra estado/fecha. La actualización real también registra un resultado agregado por fuente. Solo se persisten ID, estado y timestamp, nunca el mensaje de error. El estado es el último resultado observado, no una afirmación de disponibilidad actual.
- Filtrado de contenido: `ContentFilterableSource` es opt-in. Una fuente debe declarar IDs/etiquetas estables y evaluar cada `SManga`; las opciones se exponen únicamente para fuentes que implementen la capability. Se persisten por ID y se aplican a búsqueda global y migración normal/profunda con un helper común. Las fuentes sin capability no se filtran. No se infiere contenido adulto a partir del nombre, género o reputación; Mihon no contiene implementaciones genéricas verificables de esas reglas, y un filtro parcial no es una garantía de seguridad.
- Recursos: nuevas cadenas en `base` y `es`. Pruebas unitarias añadidas para serialización/tolerancia a entradas inválidas de clasificación y filtros de contenido.

## Validación

Se respetó la instrucción de no ejecutar Gradle localmente. Antes del push se harán comprobaciones estáticas y `git diff --check`; la compilación APK y las pruebas se validarán mediante GitHub Actions. El resultado, enlace y errores exactos de Actions se anotarán al completar cada ejecución; hasta entonces la implementación no se declara compilada.
