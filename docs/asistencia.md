# Eventos y asistencia

Al iniciar `SgaApiApp`, `AsistenciaRepository.asegurarEsquema()` aplica `src/main/resources/asistencia.sql` de manera idempotente y transaccional. Utiliza el pool PostgreSQL existente. Desplegar este backend antes del frontend que incluye Comunidad → Asistencia.

El esquema contiene eventos, columnas, asistentes y valores por casilla. La asistencia referencia `persona_natural.entidad_id`; no se agregan personas automáticamente al crear un evento. La clave única `(evento_id, persona_id)` impide duplicados desde distintos puestos. Las claves foráneas compuestas impiden mezclar asistentes y columnas de eventos diferentes. Eliminar un evento, una columna o un registro de asistencia borra sus respuestas dependientes, conservando las fichas de personas. Una persona con asistencia registrada permanece referenciada y no se puede eliminar físicamente mientras existan esos registros.

## Rutas

Prefijo: `/api/asistencia/eventos`. Todas las rutas admiten `OPTIONS` con la política CORS existente.

| Método | Ruta | Datos / resultado |
| --- | --- | --- |
| GET | `/` | Eventos con total de asistentes |
| POST | `/` | `{nombre, fecha: "YYYY-MM-DD", descripcion?: string}` → `{id}` |
| GET | `/:eventoId` | `{evento, columnas, asistentes}` en una instantánea consistente, sin caché |
| DELETE | `/:eventoId` | Eliminar evento y asistencia |
| POST | `/:eventoId/personas` | `{personaId}` → `{id, creada}`; un duplicado devuelve el mismo registro |
| DELETE | `/:eventoId/personas/:asistenciaId` | Retirar asistencia |
| POST | `/:eventoId/columnas` | `{nombre, tipo: "boolean" | "text" | "number"}` → `{id}` |
| DELETE | `/:eventoId/columnas/:columnaId` | Eliminar columna y respuestas |
| PUT | `/:eventoId/personas/:asistenciaId/valores/:columnaId` | `{valor, version}` → `{valor, version}` |

`asistenciaId` es el identificador del registro de llegada, distinto de `personaId`. Cada persona incluye nombre, RUT opcional, hora de llegada UTC y `valores`, un objeto indexado por ID de columna.

Las escrituras comparan la versión de **una sola casilla**. Para la primera escritura se envía `version: 0`; después se envía la última versión leída. El servidor incrementa la versión al guardar. Si otro puesto ya modificó la casilla, responde `409` sin sobrescribirla. El cliente debe actualizar y dejar que el operador revise el conflicto antes de volver a escribir. Los valores vacíos se guardan como `null` conservando su versión.

Las columnas booleanas comienzan sin respuesta; `false` es un “No” explícito. Nombres: 1–120 caracteres; descripciones y textos: hasta 2000; números: valor absoluto máximo de 10¹². Los nombres de columna son únicos dentro de cada evento sin distinguir mayúsculas.

El frontend consulta el detalle cada tres segundos, evita aplicar respuestas anteriores a una escritura y conserva los borradores en edición. No se requiere infraestructura de WebSockets. Se mantiene el modelo de acceso de la API existente; la navegación del frontend permite los grupos de operación diaria.

## Verificación

`sbt compile` compila el módulo. La integración usa únicamente `ASISTENCIA_TEST_JDBC_URL`; nunca toma las credenciales `DATABASE_*` ni carga `.env`. Cada ejecución crea y elimina un esquema con personas ficticias en la base indicada.

```bash
docker run --rm -d --name sga-asistencia-test \
  -e POSTGRES_PASSWORD=asistencia-test -e POSTGRES_DB=asistencia_test \
  -p 127.0.0.1:55439:5432 postgres:16-alpine

ASISTENCIA_TEST_JDBC_URL='jdbc:postgresql://127.0.0.1:55439/asistencia_test?user=postgres&password=asistencia-test' \
  sbt 'testOnly cl.familiarenacer.sga.AsistenciaIntegrationSpec'

docker stop sga-asistencia-test
```

Las ocho pruebas cubren listas vacías, personas sin RUT, altas simultáneas, tipos y validación, ediciones independientes, conflictos de versión, aislamiento entre eventos, borrado en cascada y contratos HTTP/CORS. Sin la variable de pruebas, la suite se marca como cancelada en vez de usar una base de la aplicación.

`Test / runMain cl.familiarenacer.sga.AsistenciaBrowserFixture` permite levantar solo la API de asistencia en `127.0.0.1:58089` con la misma base de pruebas y personas ficticias. Es un auxiliar de desarrollo, no el servidor de producción.
