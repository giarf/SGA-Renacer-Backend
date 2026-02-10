import io.getquill._

object DbContext {
  // Usamos SnakeCase porque en tu Postgres las columnas son "tipo_entidad"
  // pero en Scala usaremos "tipoEntidad". Quill hará el mapeo solo.
  val ctx = new PostgresJdbcContext(SnakeCase, "ctx")
}