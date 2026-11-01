# Plan: migrar la identificación por huella a SourceAFIS

Estado: propuesta, sin implementar. Fecha: 1-11-2026.

## Objetivo

Reemplazar el comparador de libfprint (Bozorth3, umbral fijo 40, acepta la
primera coincidencia) por SourceAFIS 3.18.1 en el backend del SGA, manteniendo
el lector DigitalPersona 4500 y el servicio `huellas-api` de laka.

## Por qué

| | Hoy (libfprint) | Con SourceAFIS |
|---|---|---|
| Decisión 1:N | Primera plantilla que supera 40 | Mejor puntaje, con umbral configurable |
| Precisión publicada | Ninguna para este driver | FMR 0,01 % con umbral 40; FNMR 7,5–9 % (FVC-onGoing FV-STD) |
| Datos por lectura | Todas las plantillas (~4,4 KB c/u, ~2,3 MB con 500) | Una imagen (~111 KB sin comprimir) |
| Comparación | En laka | En el backend, plantillas en memoria |
| Costo | Gratis | Gratis (Apache 2.0) |

No existe una opción gratuita mejor documentada. OpenAFIS (BSD-2) es más rápido,
pero no extrae minucias de imágenes y no publica precisión. Las alternativas más
precisas (Neurotechnology VeriFinger, Innovatrics) son de pago.

## Arquitectura

```
Lector ── laka (Python + libfprint)          Backend SGA (Scala + SourceAFIS)       Postgres
           captura imagen 384×290 gris  ──►   extrae plantilla, compara 1:N   ──►   persona_huella
           guarda imágenes por huella_id      plantillas en memoria                 (plantilla SourceAFIS)
```

- **Laka** solo captura y archiva imágenes. No compara ni recibe plantillas.
- **Backend** crea plantillas, mantiene una caché en memoria y decide la coincidencia.
- **Postgres** guarda la plantilla SourceAFIS (~470 B) y su versión. No guarda imágenes.
- **Laka no conoce personas**: archiva por `huella_id` (id de `persona_huella`).

## Fase 1 · Laka

1. Capturar imágenes con `device.capture_sync()` de libfprint (el driver
   `uru4000` admite `FP_DEVICE_FEATURE_CAPTURE`). Verificar con una prueba que la
   imagen sale entera y sin procesar.
2. Rutas nuevas, con la misma clave Bearer y el mismo bloqueo del lector:
   - `POST /api/imagen` `{timeout?}` → `{imagen: <png base64>, ancho, alto, dpi: 500}`.
   - `POST /api/huellas/{huella_id}/tomas` `{timeout?, tomas: 3}` → captura 3 tomas,
     las guarda y las devuelve.
   - `GET /api/huellas/{huella_id}/tomas` → devuelve las tomas archivadas (para regenerar).
   - `DELETE /api/huellas/{huella_id}` → borra la carpeta.
3. Archivo: `/root/huellas/datos/imagenes/<huella_id>/toma-N.png`, permisos `0700`,
   solo root.
4. Respaldar `/root/huellas/datos` (confirmar si laka tiene respaldo hoy).
5. Mantener `/api/capturar` y `/api/identificar` durante la transición.

## Fase 2 · Backend

1. `build.sbt`: `"com.machinezoo.sourceafis" % "sourceafis" % "3.18.1"` (fija; Java 11+,
   el contenedor usa Temurin 17).
2. Esquema (aplicar a mano, como el resto de huellas):
   ```sql
   ALTER TABLE persona_huella
     ADD COLUMN motor TEXT NOT NULL DEFAULT 'libfprint',
     ADD COLUMN version_motor TEXT NOT NULL DEFAULT '';
   ```
   SourceAFIS guarda la plantilla serializada en base64 en `plantilla`, con
   `motor = 'sourceafis'` y `version_motor = '3.18.1'`.
3. `HuellaMatcher`: carga al inicio todas las plantillas SourceAFIS en memoria;
   se actualiza al registrar o borrar. Identificación: un `FingerprintMatcher`
   por lectura, recorrer candidatos y quedarse con el mejor puntaje por persona
   (sumando dedos si hay dos).
4. Umbral en `HUELLA_UMBRAL` (por defecto 40 hasta calibrar). Responder también
   el puntaje para registrarlo en logs y calibrar.
5. Rutas del SGA sin cambios para el frontend:
   - `POST /api/personas/:id/huellas` → crea la fila, pide a laka 3 tomas para ese
     `huella_id`, extrae plantillas con `FingerprintImageOptions().dpi(500)`, descarta
     tomas de baja calidad (pocas minucias) y guarda.
   - `POST /api/huellero/identificar` → pide a laka una imagen, compara en memoria y
     responde la persona o `no_coincide`.
   - `DELETE /api/personas/:id/huellas/:huellaId` → borra en Postgres y en laka.
6. Tarea `POST /api/huellero/regenerar` (solo administración): pide las tomas de cada
   huella a laka y vuelve a crear plantillas. Necesaria solo al cambiar de versión.
7. Pruebas: unitarias del matcher con imágenes de FVC2002 DB1 B (dataset público) y
   de la ruta con un huellero falso.

## Fase 3 · Frontend

Cambios mínimos:

- Registrar: indicar “Pon el dedo 3 veces” y permitir registrar un segundo dedo
  (recomendado).
- Mostrar la persona identificada con confirmación visual; ya ocurre en Asistencia.
- La lectura continua sigue igual; cada vuelta pesa ~111 KB en vez de todas las plantillas.

## Fase 4 · Migración y calibración

1. Borrar las 2 huellas libfprint actuales y volver a registrarlas.
2. Registrar 20–30 personas reales con dos dedos.
3. Medir puntajes de la misma persona y de personas distintas (matriz todos contra
   todos con los logs). Fijar el umbral para que ninguna pareja distinta lo supere
   con margen.
4. Retirar `/api/capturar`, `/api/identificar` y la columna `formato` libfprint.

## Límites esperados

| Personas con huella | Recomendación |
|---|---|
| Hasta ~500 | Bien con un dedo |
| ~500–1.000 | Umbral más alto y confirmación del nombre en pantalla |
| ~1.000–3.000 | Dos dedos por persona y umbral calibrado |
| Más | Comparador comercial; regenerar plantillas desde las imágenes de laka |

## Riesgos

- **No reconocer a alguien (7,5–10 %)**: se mitiga registrando dos dedos y con buena
  calidad; siempre queda la búsqueda por nombre.
- **Pérdida de imágenes en laka**: el sistema sigue funcionando con las plantillas de
  Postgres, pero no se podría cambiar de versión sin re-registrar. Requiere respaldo.
- **Datos sensibles (Ley 21.719)**: imágenes solo en laka con acceso root, borrado
  junto con la persona, consentimiento al registrar.
- **Escala de imagen**: SourceAFIS no es invariante a escala; el 4500 es 512 dpi, se
  usará 500 dpi y se validará en la calibración.
