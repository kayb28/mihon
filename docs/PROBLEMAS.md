# Problemas del fork — MihonFix (kayb28)

Registro de problemas reportados por el usuario, su estado y quién/cuándo lo confirmó.
Una fila por problema. Convenciones de fecha y hora en `docs/REGISTRO.md` (UTC).

| Problema | Estado | Detalle |
| --- | --- | --- |
| Calor del visor | Cerrado | Confirmado por el usuario: sin calor en versiones recientes (2026-11-17). |
| Reparador: rutas WebView sin validar en dispositivo | Abierto | La ruta moderna del fallback (mundo de página, POST binario, puente legacy) nunca entregó respuestas antes del PR #1 (hallazgos 3.1–3.3) y hoy está verificada solo por código/CI/harness; sin validación en el teléfono del usuario (F4 de la auditoría del 2026-11-26). Cierre: solo con prueba en dispositivo. |
| Reparador: pasada en calma sin validación en dispositivo | Abierto | Decisiones (transitorios a la calma, corte 5xx×3, presupuesto 20 min/fuente, lo no intentado conserva su error) verificadas por tests JVM desde `00f465d`; el bucle integrado (WorkManager) no es ejecutable en JVM y nadie lo ha observado en pantalla (F4). Cierre: solo con prueba en dispositivo. |
| Reparador: desfase racha «pegajosa» vs auditoría | Abierto (decisión del usuario) | Tras confirmarse un enlace roto (racha ≥3), un código transitorio posterior NO lo degrada a TEMPORARY — semántica deliberada del código, fijada por test (`9b8be0d`), distinta de la descripción de la auditoría. Queda como está a la espera de decisión del usuario: mantenerla o degradar. |
