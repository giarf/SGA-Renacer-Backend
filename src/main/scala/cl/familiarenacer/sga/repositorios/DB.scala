package cl.familiarenacer.sga.repositorios

import io.getquill.{PostgresJdbcContext, SnakeCase}

/**
 * Objeto Singleton para el contexto de base de datos.
 * Inicializa la conexión JDBC con PostgreSQL usando configuración "ctx" de application.conf.
 * Utiliza SnakeCase para traducir automáticamente de camelCase (Scala) a snake_case (SQL).
 */
object DB {
  // Inicializamos el contexto de forma perezosa para que arranque cuando se use.
  lazy val ctx = new PostgresJdbcContext(SnakeCase, "ctx")
}
