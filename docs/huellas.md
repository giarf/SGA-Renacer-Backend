# Huellas (DigitalPersona 4500U + SourceAFIS)

El lector está conectado a laka, un PC de la casa que expone un servicio propio en
`huella.familiarenacer.cl`. El navegador nunca habla con ese servicio: el
frontend llama al backend del SGA y este le pide a laka una imagen.

```
Frontend SGA ──► api.familiarenacer.cl (SGA + SourceAFIS) ──► huella.familiarenacer.cl (laka, lector)
                         │  extrae plantilla y compara 1:N en memoria      captura imagen PNG gris
                         └── PostgreSQL: persona_huella + persona_huella_toma (plantillas)
```

Laka solo captura y archiva imágenes por `huella_id`; no conoce personas ni
compara. El backend extrae las plantillas con SourceAFIS 3.18.1, las guarda en
Postgres y mantiene todas en memoria (se cargan al arrancar y se actualizan al
registrar o borrar). Cada dedo se registra con 3 tomas; el puntaje de una persona
es el mejor entre todas sus tomas y dedos. El plan y sus decisiones están en
[`plan-sourceafis.md`](plan-sourceafis.md).

Como en apoderados, el esquema se administra directamente en la base: el backend
no ejecuta DDL de huellas al arrancar. El script está en
`src/test/resources/huellas.sql` (base nueva) y la migración desde libfprint en
`src/test/resources/huellas-migrar-sourceafis.sql`. Eliminar una persona elimina
sus huellas en Postgres.

## Configuración

| Variable | Por defecto | Uso |
| --- | --- | --- |
| `HUELLERO_URL` | `https://huella.familiarenacer.cl` | URL base del servicio del lector |
| `HUELLERO_TOKEN` | (vacío) | `Authorization: Bearer …` para las rutas normales de laka |
| `HUELLERO_TOKEN_ADMIN` | (vacío) | Clave admin de laka, solo para leer imágenes al regenerar |
| `HUELLERO_TIMEOUT_SEGUNDOS` | `40` | Espera de lectura cuando la petición no indica `timeout` |
| `HUELLA_UMBRAL` | `40` | Puntaje mínimo de SourceAFIS para aceptar una coincidencia |
| `HUELLA_MARGEN` | `0` | Diferencia mínima con la segunda persona (0 = desactivado) |
| `HUELLA_MINUCIAS_MIN` | `15` | Bajo esto la lectura es `calidad_baja` |

## Asistencia rápida

En un evento, «Lectura con huella» deja el lector esperando dedos sin
interrupción. Cada toque es **una sola llamada**
(`POST /api/asistencia/eventos/:id/huella`): el backend pide la imagen a laka,
identifica y registra la llegada, y el frontend agrega a la persona a la lista
al instante. Cada vuelta pide `timeout: 10` para liberar pronto el lector al
pausar. Sin dedo, la vuelta se repite en silencio; si no coincide o la huella
sale poco clara, se avisa y continúa.

Cada toque escribe en el log del backend:

```
huella asistencia evento=… persona=… puntaje=… captura=… envio=… extraccion=… comparacion=… registro=… total=…
huella no_coincide puntaje=… captura=… envio=… extraccion=… comparacion=…
```

Medido en pruebas (sin lector real): extracción ~28 ms, comparación 1:N ~3 ms con
pocas huellas y ~8 ms con 3 000 tomas, registro en la base ~60 ms. El resto es
captura en laka y envío por Pangolin.

## Rutas del SGA

| Método | Ruta | Datos / resultado |
| --- | --- | --- |
| GET | `/api/huellero/estado` | Siempre `200`: `{conectado: true, lector, listo, huellasEnMemoria}` o `{conectado: false, codigo, error}` |
| POST | `/api/asistencia/eventos/:id/huella` | `{timeout?}` → `201` (nueva) / `200` (ya estaba) `{persona: {id, nombreCompleto, rut, dedo}, asistenciaId, creada, puntaje, tiempos}` |
| POST | `/api/huellero/identificar` | `{timeout?}` (2–120 s) → `{id, nombreCompleto, rut, dedo}`; `404` si no coincide |
| GET | `/api/personas/:id/huellas` | `{dedos: [...], huellas: [{id, personaId, dedo, formato, calidad, registrada}]}` |
| POST | `/api/personas/:id/huellas` | `{dedo}` → 3 toques del mismo dedo; `201` con la huella (`calidad` = menor número de minucias) |
| DELETE | `/api/personas/:id/huellas/:huellaId` | Borra en Postgres, en memoria y las imágenes en laka |
| POST | `/api/huellero/regenerar` | Recrea las plantillas desde las imágenes de laka (al cambiar de versión de SourceAFIS) |

Registro: cada toma debe tener minucias suficientes y coincidir con la primera
(mismo dedo); si no, se repite solo esa toma (hasta 2 reintentos en total). Si el
primer toque ya pertenece a otra persona se responde `409 duplicada`. Si falla el
registro de un dedo nuevo no queda fila ni imágenes.

Dedos: `pulgar_derecho`, `indice_derecho`, `medio_derecho`, `anular_derecho`,
`menique_derecho` y los mismos con `_izquierdo`.

Códigos de error (`codigo`): `sin_huellas`, `no_coincide`, `sin_dedo` (408),
`ocupado` (409), `duplicada` (409), `calidad_baja` (422), `sin_conexion` (503),
`tiempo` (504) y `clave` (502, laka rechazó `HUELLERO_TOKEN`). Otros `422` traen
el mensaje del lector para mostrarlo al operador.

## Servicio del huellero (laka)

Corre en `laka` (`/root/huellas/api.py`, servicio `huellas-api`, puerto 67) y se
publica en `huella.familiarenacer.cl` mediante Pangolin. Acceso SSH a través de
`tailscale-renacer`. Detalle de sus rutas en `/root/huellas/API.md`.

- `POST /api/imagen` `{timeout?, huella_id?, toma?}` → `image/png` gris 8 bits con
  cabeceras `X-Huella-Ancho`, `X-Huella-Alto`, `X-Huella-Dpi`, `X-Huella-Captura-Ms`.
  Con `huella_id` y `toma` la archiva en `/root/huellas/datos/imagenes/<huella_id>/`.
- `GET /api/huellas/{id}/tomas`, `GET /api/huellas/{id}/tomas/{n}` → solo con la clave admin
  (`/root/huellas/api-admin.key`).
- `DELETE /api/huellas/{id}` → `{borradas}`.
- `GET /api/estado` → `{lector, listo}`.

Errores: `400` datos, `401` clave, `408` sin dedo en el plazo, `409` ocupado,
`422` lectura fallida, `503` lector desconectado.

## Pruebas

- `HuellaMatcherSpec`: extracción y búsqueda 1:N con las imágenes de ejemplo de
  SourceAFIS (`src/test/resources/huellas/`, Apache 2.0).
- `HuellasIntegrationSpec`: registro de 3 tomas, toma repetida, duplicados, asistencia
  con un toque, no coincidencia y borrado, con un huellero falso. Usa
  `ASISTENCIA_TEST_JDBC_URL` como la integración de asistencia.
