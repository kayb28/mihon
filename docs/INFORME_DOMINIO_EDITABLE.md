# INFORME P8 — Dominio editable por fuente («Editar dominio», regla from_host→to_host)

Rama: `feat/p8-domain-override` (cortada de `main` DESPUÉS del merge de P7/PR #14, como
exige la dependencia de zona de red). Encargo: PROMPT 8 y PROMPT 9 (T-P8). Fecha:
2026-11-27. Agente: GLM.

## Escenario real

Una fuente cambió de dominio y todos los enlaces viejos murieron (5 mangas afectados).
Objetivo: dentro de la APP, por fuente, pegar la URL del sitio nuevo → regla
`from_host → to_host` → toda petición al host viejo se reescribe automáticamente
(path/query/fragment intactos), sin tocar NINGUNA de las ~1000 extensiones.

## Arquitectura (hechos auditados respetados)

- Cliente OkHttp único (`NetworkHelper.client`); el fallback WebView de P7 y OkHttp
  comparten capa de red → **UN helper** (`DomainOverrideHelper`) con DOS llamantes: el
  interceptor OkHttp nuevo (`DomainOverrideInterceptor`, antes de Cloudflare/fallback) y
  `WebViewFetchFallbackInterceptor.attemptFallback` (reescritura antes del fetch WebView).
- Match por HOST exacto (cada extensión tiene su host de sitio → aislamiento natural por
  fuente; inyección de source ID innecesaria → no se activó el STOP).
- Capa pequeña propia (`core:common` envoltorio); el motor grande solo se envuelve:
  `NetworkHelper` recibe el interceptor por composición, sin reestructurar nada.
- Prioridad efectiva (D7): preferencia de extensión → regla del fork → host por defecto.

## Comportamiento → test → commit

| ID | Comportamiento | Test (JVM, `DomainOverrideTest`) | Commits |
| --- | --- | --- | --- |
| D1 | Almacén de reglas from→to persistido (PreferenceStore, JSON); CRUD; match exacto case-insensitive | `D1 rules are created…`, `D1 matching is exact-host…` | rojo `f26138745` → verde `1f95fc975` |
| D2 | OkHttp: host que matchea → reescrito a toHost conservando path/query/fragment; cualquier otra petición intacta | `D2 a matching request…`, `D2 any other request…`, `D2 rewriteRequest…` | `61ba02ec5` |
| D3 | La ruta WebView (P7) usa el MISMO helper; una petición del fallback también se reescribe | `D3 the WebView path…` + wiring (`DomainOverrideInterceptor`, NetworkHelper, attemptFallback) | `c5310d5b3` |
| D4 | Validación: URL inválida / esquema no http(s) / from==to → rechazo con motivo visible; nunca rompe la tubería | `D4 invalid input…`, `D4 a valid new URL…` | `3b6809265` + fix `882e1a080` |
| D5 | Regresión cero: sin reglas, helper devuelve la misma instancia; sin saltos extra | `D5 with zero rules…` | por construcción en `61ba02ec5` (mismo test en la serie roja→verde) |
| D6 | UI por fuente «Editar dominio» (dialog con URL, regla activa listada, borrar) + una línea de log por reescritura | `D6 every rewrite logs…` (parte JVM: log); UI Compose → checklist de dispositivo | `8e253de46` + ktlint |
| D7 | No interferir: petición que ya va al host destino (preferencia de extensión) queda intacta; aplicación única (sin ping-pong con reglas encadenadas) | `D7 a request already on…`, `D7 chained targets…` | rojo `e482741cc` → verde `5e3074eaa` |

Commits de formato/compile exigidos por ktlint/CI (no cuentan como rojo/verde): `716030095`
— no, ese era P7; aquí: `882e1a080` (escape de template), `9d1516734` (imports), `d9dbfb574`
(when-wrap), `6ec90b5f3`+`0553e9a4e` (estilo + devolución del archivo T-E que se coló por
error de árbol sucio), `85bf635b5` (orden imports + newline).

## UI (D6)

Acción «Editar dominio» en el diálogo de opciones de cada fuente (`SourceOptionsDialog`,
solo fuentes online no-stub). Diálogo: campo de URL con validación en vivo (motivo visible:
«No es una URL http/https válida» / «Ese ya es el dominio de esta fuente»), regla activa
listada (`viejo → nuevo`), y borrado de regla. El host actual se resuelve del `baseUrl` de
la fuente instalada. Strings base + es.

## Rojo / verde (CI)

Serie completa con runs anotados en `docs/REGISTRO.md`. El rojo `f26138745` corrió en CI
(run 45944738289) con los tests D1–D6 fallando por comportamiento (Spotless OK). Verdes
por comportamiento, un commit cada uno; las fallas intermedias de la serie fueron SOLO
ktlint/imports (corregidas en commits style:), jamás de comportamiento.

## STOP — no activado

No hizo falta modificar extensiones ni inyectar source ID en la cadena de peticiones: el
match por host exacto da el aislamiento por fuente de forma natural.

## Checklist de dispositivo (usuario)

1. Fuente con sitio mudado: mantener pulsada la fuente → «Editar dominio» → pegar la URL
   nueva → guardar. Navegar la fuente: debería cargar desde el sitio nuevo (si la
   estructura del sitio no cambió).
2. Regla activa visible en el diálogo; borrarla → la fuente vuelve a fallar como antes.
3. Entrada inválida («hola», «ftp://x», la misma URL del sitio) → rechazo con mensaje.
4. Con CERO reglas: comportamiento idéntico al de hoy (nada cambia).
5. Una fuente con preferencia propia de mirrors que YA apunta al sitio nuevo: sigue
   funcionando sin que la regla la moleste (D7).

## En criollo

Cuando un sitio de mangas se muda de dirección, en vez de esperar a que arreglen la
extensión: mantenés apretada la fuente en la app, «Editar dominio», pegás el link nuevo y
listo — la app le «corrige» la dirección a todas las peticiones viejas para que apunten al
sitio nuevo, y el resto de las fuentes ni se enteran. Si borrás la regla, todo vuelve como
estaba. Y si la propia extensión ya se arregló sola, nuestra regla no se mete.

---

# Ampliación — tres bordes de la auditoría externa del PR #18 (feat/p8-fixes)

Auditoría externa del PR #18 detectó tres bordes sin pulir; ninguno rompía nada hoy,
los tres se cierran en la rama `feat/p8-fixes` desde `main`, un commit verde por
comportamiento (los IDs `P8-fix1/2/3` son de este encargo, no de la serie D1-D7).

## P8-fix1 — Snackbar de confirmación al guardar/borrar

`source_action_edit_domain_saved` y `source_action_edit_domain_removed` existían en
i18n pero nadie los mostraba: los callbacks `onSave`/`onDelete` del
`SourceEditDomainDialog` en `SourcesTab.kt` solo guardaban/borraban y cerraban. Ahora
ambos llaman a `snackbarHostState.showSnackbar(...)` siguiendo el patrón de
`onClickUpdateLibrary` del mismo archivo; `saved` usa
`context.stringResource(..., host, toHost)` (extensión `Context.stringResource` de
core/common, misma que `DownloadProvider`). Sin test JVM (UI pura): criterio = el
checklist de dispositivo de abajo.

## P8-fix2 — Variante www. automática del par de reglas

`DomainOverrideStore.upsert()` guarda ahora SIEMPRE un par: la regla exacta que dio el
usuario más su gemela www., porque la mayoría de los sitios sirven el mismo contenido
con y sin «www.» y pedirle al usuario que cree dos reglas a mano —sin avisarle— era una
trampa. Simétrico en ambas direcciones:

- `from` SIN www. → se añade `www.from -> www.to` (si `to` ya traía www., se respeta tal cual).
- `from` CON www. → se añade `from-sin-www -> to-sin-www` (nunca una tercera variante
  «www.www.»; si `bareFrom` quedara vacío no hay gemela).

`delete()` borra las dos variantes del par con una sola acción del usuario.
`rules()` sigue devolviendo el mapa plano (ambas entradas visibles) y
`activeDomainRule()` en `SourcesViewModel` sigue mostrando UNA línea
(`targetFor(host)` es exacto, la gemela no duplica el texto del diálogo). El match
sigue siendo por host exacto: `cdn.old.example` sigue sin matchear (E3/„no
subdominios en general" intacto salvo el gemela documentado). Tests: 5 nuevos en
`DomainOverrideTest.kt` (`P8-fix2 *`), más las expectativas D1 actualizadas al nuevo
contrato documentado.

## P8-fix3 — El diálogo no queda colgado si el host no se resuelve

`showEditDomainDialog` resolvía el host en `Dispatchers.IO` y si `installed` no era
`HttpSource`, `baseUrl` faltaba o no parseaba, `editDomainState` quedaba con
`host=null` para siempre: ni diálogo ni aviso. Ahora, si tras la resolución el host es
null, se cierra `editDomainState` y se emite `Event.EditDomainUnavailable` (nuevo
miembro de `SourcesViewModel.Event`, patrón `FailedFetchingSources`); `SourcesTab.kt`
lo captura en el `collectLatest` existente y muestra el snackbar
`source_action_edit_domain_unavailable` (string nuevo, base + es). Test JVM no viable
sin mockear Android (`SourceManager`, `HttpSource`): documentado como caso de
checklist de dispositivo.

## Checklist de dispositivo (esta ampliación)

1. P8-fix1: guardar una regla → snackbar «Regla guardada: viejo → nuevo»; borrarla →
   snackbar «Regla de dominio eliminada».
2. P8-fix2: guardar `old.example -> new.example` y navegar la fuente sirviendo
   `www.old.example` → también debe ir al sitio nuevo (regla gemela). Borrar la regla
   elimina las dos variantes (no queda la www. huérfana).
3. P8-fix3: fuente rota o sin baseUrl → «Editar dominio» → el diálogo se cierra solo
   con el aviso «No se pudo resolver el dominio de esta fuente»; nunca queda la
   pantalla en blanco.
