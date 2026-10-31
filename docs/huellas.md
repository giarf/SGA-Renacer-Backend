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
| `HUELLERO_TIMEOUT_SEGUNDOS` | `45` | Tiempo máximo para que la persona ponga el dedo |

## Rutas del SGA

| Método | Ruta | Datos / resultado |
| --- | --- | --- |
| GET | `/api/huellero/estado` | `{conectado: true, …}` o `503` si el lector no responde |
| POST | `/api/huellero/identificar` | Lee una huella y devuelve `{id, nombreCompleto, rut, dedo}`; `404` si no coincide |
| GET | `/api/personas/:id/huellas` | `{dedos: [...], huellas: [{id, personaId, dedo, formato, calidad, registrada}]}` (sin plantillas) |
| POST | `/api/personas/:id/huellas` | `{dedo}` → lee una huella en el lector y la guarda; `201` con la huella |
| DELETE | `/api/personas/:id/huellas/:huellaId` | Elimina la huella |

Dedos: `pulgar_derecho`, `indice_derecho`, `medio_derecho`, `anular_derecho`,
`menique_derecho` y los mismos con `_izquierdo`.

Errores del lector: `503` sin conexión, `504` tiempo agotado, `422` si el
lector rechaza la lectura (mensaje en `error`, se muestra al operador) y `502`
si responde algo inválido.

## Contrato que debe implementar el servicio del huellero

Todas las respuestas son JSON. Ante un error responder `{ "error": "mensaje" }`
con un código 4xx (problema de lectura) o 5xx (falla del servicio).

- `GET /api/estado` → `{ "lector": "DigitalPersona 4500U", "listo": true }`
  (cualquier objeto; se reenvía al frontend).
- `POST /api/capturar` con `{}` → espera el dedo y responde
  `{ "plantilla": "<base64>", "formato": "ANSI_378_2004", "calidad": 85 }`.
  `formato` y `calidad` son opcionales.
- `POST /api/identificar` con
  `{ "candidatos": [{ "id": 1, "plantilla": "<base64>", "formato": "…" }] }` →
  espera el dedo, compara contra los candidatos y responde `{ "id": 1 }` con
  el `id` del candidato que coincide, o `{ "id": null }` si no hay coincidencia.

Ambas operaciones de lectura deben responder antes de `HUELLERO_TIMEOUT_SEGUNDOS`.
