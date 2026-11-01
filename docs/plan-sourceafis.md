# Plan: identificación por huella con SourceAFIS

Estado: backend y frontend implementados con pruebas; laka en curso. Actualizado: 1-11-2026.

## Objetivo

Registrar la asistencia **lo más rápido posible**: la persona pone el dedo y en
menos de un segundo queda registrada. Para eso se reemplaza el comparador de
libfprint (Bozorth3, umbral fijo, acepta la primera coincidencia) por
SourceAFIS 3.18.1 en el backend del SGA, manteniendo el lector DigitalPersona
4500 y el servicio `huellas-api` de laka.

## Por qué

| | Antes (libfprint) | Con SourceAFIS |
|---|---|---|
| Decisión 1:N | Primera plantilla que supera 40 | Mejor puntaje por persona, umbral configurable |
| Precisión publicada | Ninguna para este driver | FMR 0,01 % con umbral 40; FNMR 7,5–9 % (FVC-onGoing FV-STD) |
| Datos por toque | Todas las plantillas hacia laka (~4,4 KB c/u; ~2,3 MB con 500) | Una imagen PNG desde laka (~40–60 KB) |
| Llamadas por toque | Identificar + registrar asistencia (2 idas y vueltas) | Una sola |
| Comparación | En laka | En el backend, plantillas en memoria |
| Costo | Gratis | Gratis (Apache 2.0, incluido el 1:N) |

SourceAFIS no trae una función de búsqueda 1:N: la documentación oficial indica
construir un `FingerprintMatcher` con la huella leída y recorrer los candidatos
quedándose con el mejor puntaje. Es lo que hace `HuellaMatcher`.

Velocidad de referencia (benchmark oficial de la CLI, en su hardware):
extracción ~35 ms por imagen; comparación contra un no coincidente ~77 µs
(13 000/s por hilo). Con 1 000 personas × 3 tomas la búsqueda toma ~230 ms en un
hilo y ~60 ms en paralelo.

## Decisiones

- **Comparar en el backend, no en laka.** Comparar en laka ahorraría solo el envío
  de la imagen (~50–150 ms) y obligaría a correr Java en laka y guardar datos allí.
  Si las mediciones muestran que el envío pesa, se puede mover después solo la
  comparación a laka; Postgres sigue siendo la fuente de verdad.
- **Sin transición.** Hay dos huellas libfprint (pruebas). Se borran y se vuelven
  a registrar. No se mantienen los dos motores.
- **El consentimiento se firma fuera del sistema.** No se guarda en el SGA.
- **Laka no conoce personas**: archiva imágenes por `huella_id` (id de `persona_huella`).

## Arquitectura

```
Frontend ──1 llamada──► Backend SGA (Scala + SourceAFIS) ──► laka (Python + libfprint) ──► Lector
                         │ extrae plantilla, compara 1:N        captura imagen gris 8 bits
                         │ en memoria y registra asistencia      responde PNG binario
                         └──► Postgres: persona_huella + persona_huella_toma (plantillas)
```

## Rapidez: de dónde sale el tiempo

| Etapa | Antes | Objetivo | Cómo |
|---|---|---|---|
| Arrancar la lectura en laka | Proceso Python nuevo + `open_sync` por toque | ~0 | Proceso de captura persistente con el lector abierto |
| Captura | depende del dedo | igual | — |
| Envío laka → backend | JSON con plantillas | PNG binario ~40–60 KB | `application/octet-stream`/`image/png`, sin base64 |
| Extraer plantilla | — | ~35 ms | JVM precalentada al arrancar |
| Comparar 1:N | en laka | < 100 ms | Plantillas en memoria, `parallelStream` sobre candidatos |
| Registrar asistencia | 2.ª llamada del frontend | misma llamada | Ruta combinada |

Medido en las pruebas del backend (sin lector): extracción ~28 ms, comparación ~3 ms
con pocas huellas y ~8 ms con 3 000 tomas (14 núcleos), registro en Postgres ~60 ms.

Todo se mide: laka responde `X-Huella-Captura-Ms`, y el backend escribe en el log
`huella captura=… envio=… extraccion=… comparacion=… registro=… total=… puntaje=…`
y devuelve `tiempos` en la respuesta.

## Contrato · laka (`huella.familiarenacer.cl`)

Todas las rutas exigen `Authorization: Bearer CLAVE` (`/root/huellas/api.key`, igual a
`HUELLERO_TOKEN`), salvo las marcadas *admin*, que usan una clave aparte
(`/root/huellas/api-admin.key`, igual a `HUELLERO_TOKEN_ADMIN`). Comparten el mismo
bloqueo del lector. Errores en JSON `{error}` con los códigos de siempre:
`400` datos, `401` clave, `408` sin dedo en el plazo, `409` ocupado, `422` lectura
fallida, `503` lector desconectado.

| Método | Ruta | Datos | Respuesta |
|---|---|---|---|
| POST | `/api/imagen` | JSON `{timeout?: 2–120 (40), huella_id?: int, toma?: 1–5}` | `200 image/png` (gris 8 bits, crestas oscuras sobre fondo claro). Cabeceras `X-Huella-Ancho`, `X-Huella-Alto`, `X-Huella-Dpi: 500`, `X-Huella-Captura-Ms`. Con `huella_id`+`toma` además la archiva |
| GET | `/api/huellas/{huella_id}/tomas` *admin* | — | `{tomas: [1, 2, 3]}` |
| GET | `/api/huellas/{huella_id}/tomas/{n}` *admin* | — | `200 image/png` |
| DELETE | `/api/huellas/{huella_id}` | — | `{borradas: n}` (borra la carpeta; 0 si no existía) |
| GET | `/api/estado` | — | igual que antes |

- Archivo: `/root/huellas/datos/imagenes/<huella_id>/toma-<n>.png`, permisos `0700`/`0600`.
- Un dedo que se queda puesto no debe producir dos capturas seguidas si se puede
  evitar (esperar a que se retire antes de aceptar la siguiente).
- `/api/capturar` y `/api/identificar` se retiran cuando el backend nuevo esté
  desplegado (no hay transición).

## Contrato · backend (`api.familiarenacer.cl`)

| Método | Ruta | Datos | Respuesta |
|---|---|---|---|
| POST | `/api/asistencia/eventos/:eventoId/huella` | `{timeout?: 2–120}` | `200 {persona: {id, nombreCompleto, rut, dedo}, asistenciaId, creada, puntaje, tiempos: {capturaMs, envioMs, extraccionMs, comparacionMs, registroMs, totalMs}}` |
| POST | `/api/huellero/identificar` | `{timeout?}` | `200 {id, nombreCompleto, rut, dedo}` (se mantiene, ahora con SourceAFIS) |
| POST | `/api/personas/:id/huellas` | `{dedo}` | `201 {id, personaId, dedo, formato: "sourceafis-3.18.1", calidad, registrada}`; pide 3 toques |
| DELETE | `/api/personas/:id/huellas/:huellaId` | — | Borra en Postgres y en laka |
| GET | `/api/huellero/estado` | — | igual que antes |
| POST | `/api/huellero/regenerar` | — | Solo para cambiar de versión de SourceAFIS; usa `HUELLERO_TOKEN_ADMIN` |

Códigos: `sin_huellas`, `no_coincide`, `sin_dedo` (408), `ocupado` (409),
`sin_conexion` (503), `tiempo` (504), `clave` (502) y dos nuevos: `calidad_baja`
(422: la toma tiene pocas minucias o no coincide con la primera) y `duplicada`
(409: al registrar, la huella ya pertenece a otra persona).

Los cuerpos JSON se aceptan con `Content-Type: text/plain` (el frontend lo usa
para evitar preflight).

### Registro

1. Inserta o reutiliza la fila `persona_huella` (persona, dedo) para obtener `huella_id`.
2. Pide 3 tomas a laka (`/api/imagen` con `huella_id` y `toma`, 25 s cada una). Si una
   toma tiene pocas minucias o no coincide con la primera (otro dedo), repite solo esa
   toma (hasta 2 reintentos en total; peor caso ~125 s). Si la primera toma ya
   pertenece a otra persona responde `duplicada`.
3. Guarda las 3 plantillas en `persona_huella_toma` y actualiza el caché.
4. `calidad` = menor número de minucias entre las tomas.
5. Si falla el registro de un dedo nuevo, se borra la fila reservada y sus imágenes en laka.

### Identificación

Un `FingerprintMatcher` por toque; compara contra todas las tomas de todas las
huellas en memoria; puntaje por persona = máximo entre sus tomas y dedos.
Coincide si supera `HUELLA_UMBRAL` (por defecto 40). Si el mejor y el segundo
(otra persona) quedan a menos de `HUELLA_MARGEN` (por defecto 0, desactivado), se
responde `no_coincide` para no confundir personas.

### Esquema (aplicar a mano antes del despliegue)

```sql
DELETE FROM persona_huella;                       -- las 2 huellas libfprint de prueba
ALTER TABLE persona_huella DROP COLUMN plantilla;
ALTER TABLE persona_huella ALTER COLUMN formato SET DEFAULT 'sourceafis-3.18.1';
CREATE TABLE persona_huella_toma (
  huella_id INTEGER NOT NULL REFERENCES persona_huella(id) ON DELETE CASCADE,
  toma SMALLINT NOT NULL CHECK (toma BETWEEN 1 AND 5),
  plantilla BYTEA NOT NULL,
  minucias INTEGER NOT NULL,
  PRIMARY KEY (huella_id, toma)
);
```

El script completo queda en `src/test/resources/huellas.sql`.

### Configuración nueva

| Variable | Por defecto | Uso |
|---|---|---|
| `HUELLA_UMBRAL` | `40` | Puntaje mínimo para aceptar |
| `HUELLA_MARGEN` | `0` | Diferencia mínima con la segunda persona |
| `HUELLA_MINUCIAS_MIN` | `15` | Bajo esto la toma es `calidad_baja` |
| `HUELLERO_TOKEN_ADMIN` | (vacío) | Clave admin de laka (regenerar) |

## Contrato · frontend

- `HuellaContinua.vue` usa `POST /asistencia/eventos/:id/huella` (una llamada por
  toque) y agrega a la persona a la lista local sin esperar el sondeo de 3 s.
- Registro: «Pon el mismo dedo 3 veces, levantándolo entre cada toque»; espera
  hasta 150 s; sugiere registrar un segundo dedo; muestra `calidad_baja`.
- Muestra a la persona identificada con su nombre grande y sonido (ya existe).

## Orden de despliegue

1. **Laka**: rutas nuevas (aditivas). Probar `/api/imagen` con el lector.
2. **Base**: aplicar el SQL de arriba.
3. **Backend**: push a GitHub (despliegue automático). Variables nuevas en el entorno.
4. **Frontend**: push.
5. Volver a registrar las 2 huellas de prueba y medir tiempos con los logs.
6. Retirar `/api/capturar` y `/api/identificar` de laka.

## Calibración

1. Registrar 20–30 personas reales, idealmente con dos dedos.
2. Con los puntajes de los logs, armar la matriz misma persona / distinta persona.
3. Complementar las parejas distintas con FVC2002 DB1 B (público), porque 30
   personas no bastan para estimar falsos positivos.
4. Fijar `HUELLA_UMBRAL` (y `HUELLA_MARGEN` si hace falta) con margen.

## Límites esperados

| Personas con huella | Recomendación |
|---|---|
| Hasta ~500 | Bien con un dedo |
| ~500–1 000 | Umbral más alto y confirmación del nombre en pantalla |
| ~1 000–3 000 | Dos dedos por persona y umbral calibrado |
| Más de ~10 000 | Filtrar candidatos o comparador comercial |

## Riesgos

- **No reconocer a alguien (7,5–10 %)**: dos dedos y 3 tomas por dedo; siempre queda
  la búsqueda por nombre.
- **Imagen distinta a lo esperado**: libfprint puede entregar la imagen invertida o
  procesada. Se verifica en laka antes de seguir.
- **Escala**: el 4500 es 512 dpi; se declara 500 dpi y se valida en la calibración.
- **Pérdida de imágenes en laka**: el sistema sigue funcionando con las plantillas de
  Postgres; solo impide regenerar. Respaldar `/root/huellas/datos`.
- **Datos sensibles (Ley 21.719)**: imágenes solo en laka, rutas de lectura con clave
  admin aparte, borrado junto con la huella.
