# Adopciones upstream — v0.21.4 y siguientes

Seguimiento de lo que el upstream (mihon-app/mihon y la comunidad Keiyoushi) ya hizo o
planea y que este fork debe adoptar en el rebase a v0.21.4 (o después). Tabla verificada
el 2026-11-27 como parte de T-D1 (PROMPT 9); cada fila se cierra cuando la adopción entra
en `main` con su commit/PR propio.

| # | Adopción upstream | Evidencia verificada | Estado en el fork |
| --- | --- | --- | --- |
| 1 | Rebase a v0.21.4 del upstream | Upstream liberó v0.21.4 (2026-11); el fork está en base v0.21.3 (Release `v0.21.3-fork.1`) | Pendiente — encargo futuro; verificar cambios de breaking en `NetworkHelper`/visores antes |
| 2 | PR #3786 upstream: WebP truncado con re-descarga única y descarte de caché | `INFORME_REPARADOR_ENLACES_B1-B4.md` y fila «Fallo D» de `PROBLEMAS.md` ya citan el PR; implementa la ruta (a) aprobada para Bloque B | Pendiente — entra con el rebase (Fallo D, ruta b) |
| 3 | Revisar la caché de red del upstream al rebase | Auditoría del reparador (2026-11-26): la caché OkHttp (5 MiB) puede servir respuestas rancias tras un cambio de dominio; el upstream ajustó directivas de caché | Pendiente — mirar en el rebase; hoy `NetworkHelper` no la toca (solo envuelve) |
| 4 | Pins de CI: `actions/setup-java` v6.3.0, `actions/setup-gradle` v6.4.1, runner `ubuntu-26.04` | Upstream actualizó imágenes/pins en sus workflows; el fork ya usa `ubuntu-26.04` y `setup-gradle@v6.4.1` en `build_apk.yml` (verificado en el workflow actual) | Parcial — confirmar `setup-java` al rebase; no tocar workflows fuera de eso (regla T-D1/V3) |
| 5 | Port de las extensiones embebidas (MHScans, Emperor Scan) al API nuevo de extensiones | `build_apk.yml` compila ambas extensiones embebidas; el upstream movió el API de extensiones (extension-lib 1.5) y el port evita que se rompan en el rebase | Pendiente — hacer junto al rebase v0.21.4 |
| 6 | Recetas de la comunidad (issues/discusiones Mihon): #4309, #3017, #4305, #3882, #4188, #4220, #4272, #4106, #4147, #4310, #4301, #4246, #565+#3661 | `SOURCE_MANAGEMENT_RESEARCH.md` documenta estas recetas (mirrors por extensión, preferencias generadas `baseUrl { mirrors(...) } / custom(...) }` via `ConfigurableSource`, y las limitaciones que motivaron T-P8) | Activo — T-P8 (dominio editable por extensión) es la respuesta del fork; el resto se evalúa tras el rebase |

**Pendiente de aportaciones del usuario:** esta tabla recoge lo verificado a fecha
2026-11-27; el usuario puede añadir filas nuevas (o pedir el cierre de las existentes)
en cualquier momento — se registran en `docs/REGISTRO.md` con fecha y fuente.
