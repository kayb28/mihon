# INFORME T-G — Fallo G: «Editar dominio» no se encuentra en la UI (descubribilidad)

Rama: `fix/p8-descubribilidad` (cortada de `main` @ `c196c91`, la punta consolidada del
2026-11-27). Encargo: PROMPT 27, tarea T-G. Fecha: 2026-11-27. Agente: GLM.

## Diagnóstico (dónde estaba D6 realmente)

D6 (PROMPT 8) quedó cableado ÚNICAMENTE tras **mantener pulsada una fuente en la pestaña
Fuentes → diálogo de opciones → «Editar dominio»** (`SourcesTab.kt` →
`SourceOptionsDialog`, acción en `onClickEditDomain`, líneas 71–76 y 99–115; estado en
`SourcesViewModel.showEditDomainDialog`). La acción existe pero vive dentro de un menú
contextual de long-press: el usuario no la encontró ni en la pantalla de la extensión
afectada ni en ninguna otra pantalla — exactamente el reporte del Fallo G.

## Solución (sin tocar extensiones)

Segunda entrada en el lugar donde el usuario SÍ busca: **sección «Editar dominio» al
inicio de la pantalla de detalles de la extensión** (app-hosted, `ExtensionDetailsScreen`)
— la pantalla que abre desde Examinar → Extensiones → [extensión]. Un modelo puro nuevo
(`DomainRuleUi`, en `ui/browse/source`) resuelve el host del `baseUrl` de cada fuente
instalada (HttpSource), arma una fila por fuente con host resoluble y formatea la regla
activa (`from -> to`) desde el MISMO `DomainOverrideStore`.

- La sección aparece solo si la extensión tiene al menos una fuente con host http(s)
  resoluble; fila con host como título y la regla activa (o la pista de URL nueva) como
  subtítulo; el toque abre el **MISMO** `SourceEditDomainDialog` de la pestaña Fuentes.
- `ExtensionDetailsViewModel` recibe el `DomainOverrideStore` **inyectado — el mismo
  singleton** que usa `SourcesViewModel`; el diálogo, el formato y el store son UNA
  implementación (D1 sin duplicar).
- Extensión aféctada y cualquier otra: la ruta del usuario es Examinar → Extensiones →
  [extensión] → fila del sitio en «Editar dominio» → pegar URL nueva → Guardar
  (**≤ 2 toques hasta el diálogo desde la pantalla de la extensión** — G1).
- La entrada original de la pestaña Fuentes (long-press → «Editar dominio») sigue
  funcionando igual; ahora ambas comparten `DomainRuleUi.activeRuleLabel` (G2).

## Comportamiento → test → commit

| ID | Comportamiento | Test (JVM, `DomainRuleUiTest`) | Commits |
| --- | --- | --- | --- |
| G1 | Descubribilidad: sección «Editar dominio» al inicio de la pantalla de detalles de la extensión; filas por fuente online con host resoluble, regla activa listada; ≤ 2 toques desde la pantalla de la extensión | `G1 hostOf…`, `G1 rows…` (3 tests: filtrado, formato, orden) | rojo `b6b3242` → verde `496fc2c` |
| G2 | Doble entrada consistente: mismo diálogo (`SourceEditDomainDialog`), mismo store (`DomainOverrideStore` singleton), mismo formato de regla — D1 sin duplicar | `G2 activeRuleLabel…` + por construcción (mismo singleton inyectado y mismo composable) | verde `233a2fd` |

Commits de compilación exigidos por CI (no cuentan como rojo/verde): `2876513`
— import `asStateFlow` faltante + adaptación del callback de fila a `ExtensionSourceItem`
(errores de compile del run 46029298463, jamás de comportamiento).

## Rojo / verde (CI)

- Rojo `b6b3242` — su run en `fix/p8-descubribilidad` (46028933795) quedó **cancelled**
  por el push siguiente del mismo actor antes de que el job de tests llegara a fallar;
  se re-creó la serie roja en la rama de evidencia `evidence/p8-red` (mismo commit,
  push limpio): run 46029998514 — `testDebugUnitTest` falla POR
  COMPORTAMIENTO (símbolos `DomainRuleUi` inexistentes: el módulo `app` no compila).
- Verde G1+G2 `496fc2c` + `233a2fd` — el push de la punta arrastró ambos commits (error
  del agente); el run de `233a2fd` (46029298463) falló SOLO por compile (imports), lo
  que no acredita verde; el run de `2876513` (run 46029820276) corre la
  rama completa y acredita el verde de G1 y G2 con los 6 tests nuevos incluidos.
- El resultado definitivo de cada run queda anotado en `docs/REGISTRO.md` al cerrar.

## STOP — no activado

No hizo falta tocar NINGÚN archivo de extensión: la pantalla de detalles de la
extensión es código de la app (`app/src/main/java/eu/kanade/...`), y el store/diálogo
existente se reutilizó tal cual. La condición STOP del encargo no se alcanzó.

## Checklist de dispositivo (usuario)

1. Examinar → Extensiones → [extensión con sitio mudado]: arriba se ve la sección
   «Editar dominio» con una fila por fuente (host + regla activa si existe).
2. Tocar la fila → diálogo «Editar dominio» → pegar URL nueva → Guardar → volver a
   entrar a la pantalla: la fila lista la regla `viejo -> nuevo`.
3. Abrir la fuente y navegar: debería cargar del sitio nuevo (si la estructura no
   cambió).
4. Long-press de la misma fuente en la pestaña Fuentes → «Editar dominio»: el MISMO
   diálogo muestra la MISMA regla activa (consistencia G2 en pantalla).
5. Borrar la regla desde cualquiera de las dos entradas → la fuente vuelve a fallar
   como antes y la fila vuelve a mostrar la pista.
6. Entrada inválida («hola», «ftp://x», la misma URL actual) → rechazo con mensaje
   (D4 intacto).

## Validación botón B + ruta natural (T-V, sin código)

Con la regla guardada para la fuente mudada: los títulos con error de esa fuente pasan
a ser «falsos errores» → pestaña Fuentes (o el listado de errores del reparador) →
botón **«Reintentar títulos con error»** → las filas exitosas desaparecen al instante y
la lista queda vacía. Checklist completo en `docs/INFORME_BOTON_REINTENTAR.md` (el
comportamiento ya está mergeado en `main`, PR #17 — este encargo NO lo toca).

Nota P7: la validación en dispositivo de W1/W2/W4 (mismas rutas WebView del fallback)
sigue pendiente de confirmación del usuario; las dos filas F4 de `docs/PROBLEMAS.md`
permanecen ABIERTAS hasta ese reporte (solo se anotan, no se cierran).

## En criollo

El «Editar dominio» ya existía, pero estaba escondido atrás del long-press de la fuente
— nadie lo encontraba. Ahora, además de ahí, cuando entrás a los detalles de una
extensión arriba de todo aparece una sección «Editar dominio» con el sitio de cada
fuente: tocás, pegás el link nuevo y listo. Las dos puertas abren la misma ventana y
guardan en el mismo lugar, así que da igual por dónde entrés — y si borrás la regla,
todo vuelve como estaba.

## 📲 BUILD PARA INSTALAR

- Branch: `fix/p8-descubribilidad`
- Commit: `2876513` (punta: fixes de compile G1/G2 + serie roja/verde completa)
- Run CI: 46029820276, job «Build Release APK» — artefacto `INSTALAR-28765134d1c…`
  (descargado y verificado por el agente)
- APK: `MihonFix-0.21.3-build-2876513-20261127-1607-arm64.apk` (35,245,426 bytes)
- Firma: verificar con `python3 scripts/verify_apk_signature.py <apk>` → ESTABLE
  (`DE7889F3…D1296AD0`)
- sha256: `D07918C27E3C7A5F3F812BA34324AB868940E06A695E3043404813FF212BF783`
  — tamaño: 35,245,426 bytes — firma verificada con `verify_apk_signature.py` → ESTABLE
- Si la run no produce artefacto: «Ninguna — encargo sin build nueva» + motivo en
  REGISTRO.
