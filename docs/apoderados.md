# Apoderados

El esquema de apoderados se administra directamente en la base de datos; el
backend no ejecuta DDL de apoderados al arrancar. La copia en
`src/test/resources/apoderados.sql` se usa solo para pruebas. Desplegar
el backend antes del frontend. No se crean vínculos automáticamente a partir de
teléfonos o apellidos: el operador selecciona las personas y confirma el vínculo.

- `GET /api/personas/:id/apoderados`
- `GET /api/personas/:id/personas-a-cargo`
- `PUT /api/personas/:id/apoderados/:apoderadoId`
- `DELETE /api/personas/:id/apoderados/:apoderadoId`

El PUT crea o actualiza `{parentesco, esContactoPrincipal, observaciones}`.
Parentescos: Madre, Padre, Abuela, Abuelo, Tutor/a, Otro. Al elegir un principal,
se desmarca el anterior dentro de la misma transacción. El DELETE elimina solo
la relación. Las claves foráneas impiden borrar personas con vínculos activos.

En editar persona, el panel permite gestionar ambas direcciones. Sus botones
guardan independientemente del formulario principal.

Familias también ofrece `/api/familias/:id/miembros` y
`/api/familias/:id/miembros/:personaId`, conservando las rutas anteriores.
La eliminación de miembros actualiza ambas representaciones en una transacción
y devuelve éxito cuando se eliminó la membresía, incluso sin rol beneficiario.

Prueba contra una base desechable mediante `APODERADOS_TEST_JDBC_URL`:
`sbt 'testOnly cl.familiarenacer.sga.ApoderadosIntegrationSpec'`.

La creación desde asistencia reutiliza el texto buscado. Dos palabras se
precargan como nombre y apellido; los textos más largos se conservan completos
en nombres para que el operador distribuya los apellidos, sin perder palabras.
Un RUT se precarga en su propio campo.
