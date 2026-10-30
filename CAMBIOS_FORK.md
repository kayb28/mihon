# CAMBIOS_FORK.md — Manifiesto de la edicion kayb28

> Generado: 2026-10-31 · Rama de integracion: `integration` (commit 5567a8026, CI verde run #12)
> Base: `main` del fork (d96a1ce) = upstream Mihon post-v0.21.3 + workflow unzip.yml
> applicationId: `app.mihonfix` (sin Firebase; telemetria desactivada en CI)

Total de archivos cambiados respecto a la base: **48** (16 anadidos, 30 modificados, 2 eliminados)

## Mapa por funcionalidad

### Reparador de enlaces (link fixer) (6 archivos)
```
A	app/src/main/java/eu/kanade/tachiyomi/data/library/UpdateErrorStore.kt
A	app/src/main/java/mihon/feature/library/errors/UpdateErrorsScreen.kt
A	app/src/main/java/mihon/feature/library/errors/UpdateErrorsViewModel.kt
A	app/src/main/java/mihon/feature/migration/repair/RepairLinksScreen.kt
A	app/src/main/java/mihon/feature/migration/repair/RepairLinksViewModel.kt
A	app/src/main/java/mihon/feature/migration/repair/TitleMatcher.kt
```

### Gestion de fuentes (source management) (20 archivos)
```
M	app/src/main/java/eu/kanade/domain/source/service/SourcePreferences.kt
M	app/src/main/java/eu/kanade/presentation/browse/SourcesFilterScreen.kt
M	app/src/main/java/eu/kanade/presentation/browse/SourcesScreen.kt
A	app/src/main/java/eu/kanade/tachiyomi/data/source/SourceHealthStore.kt
A	app/src/main/java/eu/kanade/tachiyomi/source/ContentFilterExtensions.kt
M	app/src/main/java/eu/kanade/tachiyomi/ui/browse/source/SourcesFilterScreen.kt
M	app/src/main/java/eu/kanade/tachiyomi/ui/browse/source/SourcesFilterViewModel.kt
M	app/src/main/java/eu/kanade/tachiyomi/ui/browse/source/SourcesTab.kt
M	app/src/main/java/eu/kanade/tachiyomi/ui/browse/source/SourcesViewModel.kt
M	app/src/main/java/eu/kanade/tachiyomi/ui/browse/source/globalsearch/GlobalSearchViewModel.kt
M	app/src/main/java/eu/kanade/tachiyomi/ui/browse/source/globalsearch/SearchViewModel.kt
M	app/src/main/java/mihon/feature/migration/list/search/SmartSourceSearchEngine.kt
A	app/src/test/java/eu/kanade/tachiyomi/data/source/SourceHealthRecordTest.kt
A	domain/src/main/java/tachiyomi/domain/source/model/SourceClassification.kt
A	domain/src/main/java/tachiyomi/domain/source/model/SourceContentFilterEntry.kt
A	domain/src/test/java/tachiyomi/domain/source/model/SourceClassificationTest.kt
A	domain/src/test/java/tachiyomi/domain/source/model/SourceContentFilterEntryTest.kt
M	i18n/src/commonMain/moko-resources/base/strings.xml
M	i18n/src/commonMain/moko-resources/es/strings.xml
A	source-api/src/main/kotlin/eu/kanade/tachiyomi/source/ContentFilterableSource.kt
```

### Agrupacion de biblioteca (4 archivos)
```
M	app/src/main/java/eu/kanade/presentation/library/LibrarySettingsDialog.kt
M	app/src/main/java/eu/kanade/tachiyomi/data/library/LibraryUpdateWorker.kt
M	app/src/main/java/eu/kanade/tachiyomi/ui/library/LibraryViewModel.kt
M	domain/src/main/java/tachiyomi/domain/library/service/LibraryPreferences.kt
```

### Migracion (6 archivos)
```
M	app/src/main/java/eu/kanade/tachiyomi/ui/browse/migration/search/MigrateSearchViewModel.kt
M	app/src/main/java/mihon/feature/migration/config/MigrationConfigScreen.kt
M	app/src/main/java/mihon/feature/migration/config/MigrationConfigScreenSheet.kt
M	app/src/main/java/mihon/feature/migration/list/MigrationListScreen.kt
M	app/src/main/java/mihon/feature/migration/list/MigrationListScreenContent.kt
M	app/src/main/java/mihon/feature/migration/list/MigrationListViewModel.kt
```

### i18n / infra (4 archivos)
```
M	.github/workflows/build.yml
A	.github/workflows/build_apk.yml
D	.github/workflows/unzip.yml
M	app/build.gradle.kts
```

### Otros (8 archivos)
```
A	SOURCE_MANAGEMENT_RESEARCH.md
M	app/src/main/java/eu/kanade/presentation/browse/ExtensionsScreen.kt
M	app/src/main/java/eu/kanade/presentation/more/MoreScreen.kt
M	app/src/main/java/eu/kanade/tachiyomi/ui/browse/extension/ExtensionsViewModel.kt
M	app/src/main/java/eu/kanade/tachiyomi/ui/main/MainActivity.kt
M	app/src/main/java/eu/kanade/tachiyomi/ui/more/MoreTab.kt
M	core/common/src/main/kotlin/tachiyomi/core/common/Constants.kt
D	mihonfix-phases-1-2.zip
```

## Notas para futuras actualizaciones de Mihon

1. Al salir una version nueva (ej. v0.22.0), NO hacer merge de ramas viejas: repetir este proceso de reconstruccion (parche semantico sobre el arbol nuevo).
2. `strings.xml` (base y es) SIEMPRE se fusiona por union de claves, nunca se sobreescribe: upstream agrega cadenas en cada version.
3. Las clases del lector (`DownloadStream`, `ReaderPage`, WebGPU) evolucionan rapido upstream: revisar las anotaciones `downloadStream`/health antes de portar.
4. `LibraryUpdateWorker.kt` contiene ganchos de ambas funciones (UpdateErrorStore + SourceHealthStore + KEY_SOURCE_ID): es el archivo mas delicado de portar.
5. `fix-0.21.3` es una rama obsoleta (puerto viejo sobre v0.21.3); no usarla como base nunca mas.


### Agrupacion de biblioteca por fuente (añadido en integration)
```
A	domain/src/main/java/tachiyomi/domain/library/model/LibraryGroup.kt
M	domain/src/main/java/tachiyomi/domain/library/service/LibraryPreferences.kt
M	app/src/main/java/eu/kanade/tachiyomi/ui/library/LibraryViewModel.kt
M	app/src/main/java/eu/kanade/presentation/library/LibrarySettingsDialog.kt
M	app/src/main/java/eu/kanade/tachiyomi/ui/library/LibraryTab.kt
M	i18n/src/commonMain/moko-resources/base/strings.xml
M	i18n/src/commonMain/moko-resources/es/strings.xml
```
Tabs virtuales por sourceId; sort global cuando no es BY_DEFAULT; refresh usa sourceId del worker.
