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
