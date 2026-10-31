# Huellas (DigitalPersona 4500U)

El lector está conectado a otro PC que expone un servicio propio en
`huella.familiarenacer.cl`. El navegador nunca habla con ese servicio: el
frontend llama al backend del SGA y este reenvía la petición al huellero.

```
Frontend SGA ──► api.familiarenacer.cl (SGA) ──► huella.familiarenacer.cl (PC del lector)
                         │
                         └── PostgreSQL: persona_huella (plantillas)
```

Las plantillas se guardan en la base del SGA, en `persona_huella`. Como en
apoderados, el esquema se administra directamente en la base: el backend no
ejecuta DDL de huellas al arrancar. El script está en
`src/test/resources/huellas.sql`; aplicarlo antes de desplegar en una base nueva. El servicio del lector no
necesita base de datos: solo captura y compara contra los candidatos que le
envía el SGA. Hay una plantilla por persona y dedo; volver a registrar un dedo
la reemplaza. Eliminar una persona elimina sus huellas.

## Configuración

| Variable | Por defecto | Uso |
| --- | --- | --- |
| `HUELLERO_URL` | `https://huella.familiarenacer.cl` | URL base del servicio del lector |
| `HUELLERO_TOKEN` | (vacío) | Si existe, se envía como `Authorization: Bearer …` |
| `HUELLERO_TIMEOUT_SEGUNDOS` | `60` | Espera máxima del SGA; el lector corta a los 40 s |

## Asistencia continua

En un evento, «Lectura con huella» deja el lector esperando dedos sin
interrupción: cada huella reconocida registra la llegada y vuelve a esperar.
Cada vuelta pide `timeout: 10`, así el lector queda libre en pocos segundos
cuando se pausa (por ejemplo, al crear una persona y registrar su huella).
Sin dedo, la vuelta se repite en silencio; si la huella no coincide, se avisa y
continúa. El modo se detiene al salir del evento.

## Rutas del SGA

| Método | Ruta | Datos / resultado |
| --- | --- | --- |
| GET | `/api/huellero/estado` | Siempre `200`: `{conectado: true, lector, listo}` o `{conectado: false, codigo, error}`. Lo usa el indicador del encabezado |
| POST | `/api/huellero/identificar` | `{timeout?}` (2–120 s). Lee una huella y devuelve `{id, nombreCompleto, rut, dedo}`; `404` si no coincide |
| GET | `/api/personas/:id/huellas` | `{dedos: [...], huellas: [{id, personaId, dedo, formato, calidad, registrada}]}` (sin plantillas) |
| POST | `/api/personas/:id/huellas` | `{dedo}` → lee una huella en el lector y la guarda; `201` con la huella |
| DELETE | `/api/personas/:id/huellas/:huellaId` | Elimina la huella |

Dedos: `pulgar_derecho`, `indice_derecho`, `medio_derecho`, `anular_derecho`,
`menique_derecho` y los mismos con `_izquierdo`.

Los errores incluyen `codigo` cuando el frontend debe reaccionar distinto:
`sin_huellas`, `no_coincide`, `sin_dedo` (408, nadie puso el dedo en el plazo),
`ocupado` (409), `sin_conexion` (503), `tiempo` (504) y `clave` (502, el
huellero rechazó `HUELLERO_TOKEN`).

Errores del lector: `503` sin conexión, `504` tiempo agotado, `422` si el
lector rechaza la lectura (mensaje en `error`, se muestra al operador) y `502`
si responde algo inválido.

## Servicio del huellero (laka)

Corre en `laka` (`/root/huellas/api.py`, servicio `huellas-api`, puerto 67) y se
publica en `huella.familiarenacer.cl` mediante Pangolin. La clave está en
`/root/huellas/api.key` y debe coincidir con `HUELLERO_TOKEN`. Todas las rutas
exigen `Authorization: Bearer CLAVE`.

- `GET /api/estado` → `{ "lector": "DigitalPersona 4500U", "listo": true }`.
- `POST /api/capturar` con `{}` → la persona pone y retira el mismo dedo cinco
  veces; responde `{ "plantilla": "<base64>", "formato": "libfprint-uru4000" }`.
  La plantilla no se guarda en laka.
- `POST /api/identificar` con `{ "candidatos": [{ "id": 1, "plantilla": "<base64>" }] }`
  → un toque; responde `{ "id": 1 }` o `{ "id": null }`.

Ambas aceptan `timeout` (2–120 s, 40 por defecto). Respuestas de error:
`400` datos inválidos, `401` clave, `408` sin dedo en el plazo, `409` lector
ocupado, `422` lectura fallida, `503` lector desconectado. Las plantillas son de libfprint: solo
sirven con este lector y este driver.

Las rutas antiguas (`/registros`, `/identificaciones`, `/personas`) siguen
funcionando con la base local de laka, independiente del SGA.
