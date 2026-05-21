package cl.familiarenacer.sga.repositorios

import cl.familiarenacer.sga.modelos.{Comuna, Region}
import java.sql.DriverManager
import scala.io.Source
import scala.util.Using

class UbicacionRepository {
  private def withConnection[A](fn: java.sql.Connection => A): A = {
    val host = sys.env.getOrElse("DATABASE_HOST", "localhost")
    val port = sys.env.getOrElse("DATABASE_PORT", "5432")
    val database = sys.env.getOrElse("DATABASE_NAME", "sga_renacer")
    val user = sys.env.getOrElse("DATABASE_USER", "postgres")
    val password = sys.env.getOrElse("DATABASE_PASSWORD", "")
    val conn = DriverManager.getConnection(s"jdbc:postgresql://$host:$port/$database", user, password)
    try fn(conn)
    finally conn.close()
  }

  def asegurarEsquemaUbicaciones(): Unit = withConnection { conn =>
    val statement = conn.createStatement()
    try {
      statement.execute(
        """
        CREATE TABLE IF NOT EXISTS region (
          id INTEGER PRIMARY KEY,
          nombre TEXT NOT NULL UNIQUE
        )
        """
      )
      statement.execute(
        """
        CREATE TABLE IF NOT EXISTS comuna (
          id INTEGER PRIMARY KEY,
          region_id INTEGER NOT NULL REFERENCES region(id),
          nombre TEXT NOT NULL,
          UNIQUE(region_id, nombre)
        )
        """
      )
      seedUbicaciones(statement)
    } finally statement.close()
  }

  def listarRegiones(): List[Region] = withConnection { conn =>
    val ps = conn.prepareStatement("SELECT id, trim(nombre) AS nombre FROM region ORDER BY id")
    try {
      val rs = ps.executeQuery()
      val buffer = scala.collection.mutable.ListBuffer.empty[Region]
      while (rs.next()) {
        buffer += Region(rs.getInt("id"), rs.getString("nombre"))
      }
      buffer.toList
    } finally ps.close()
  }

  def listarComunas(regionId: Option[Int]): List[Comuna] = withConnection { conn =>
    val sql = regionId match {
      case Some(_) => "SELECT id, region_id, trim(nombre) AS nombre FROM comuna WHERE region_id = ? ORDER BY nombre"
      case None => "SELECT id, region_id, trim(nombre) AS nombre FROM comuna ORDER BY nombre"
    }
    val ps = conn.prepareStatement(sql)
    try {
      regionId.foreach(ps.setInt(1, _))
      val rs = ps.executeQuery()
      val buffer = scala.collection.mutable.ListBuffer.empty[Comuna]
      while (rs.next()) {
        buffer += Comuna(rs.getInt("id"), rs.getInt("region_id"), rs.getString("nombre"))
      }
      buffer.toList
    } finally ps.close()
  }

  private def seedUbicaciones(statement: java.sql.Statement): Unit = {
    val stream = Option(getClass.getClassLoader.getResourceAsStream("region-comuna.sql"))
      .getOrElse(throw new IllegalStateException("No se encontró region-comuna.sql"))
    val sql = Using.resource(Source.fromInputStream(stream, "UTF-8"))(_.mkString)
    sql.split(";")
      .map(_.trim)
      .filter(_.nonEmpty)
      .map(sanitizeInsert)
      .foreach(statement.execute)
  }

  private def sanitizeInsert(raw: String): String = {
    val quoted = "\"([^\"]*)\"".r.replaceAllIn(raw, m => s"'${m.group(1).replace("'", "''")}'")
    val trimmed = quoted.replaceAll("'([^']*?)\\s+'", "'$1'")
    if (trimmed.toLowerCase.startsWith("insert into region")) s"$trimmed ON CONFLICT (id) DO NOTHING"
    else if (trimmed.toLowerCase.startsWith("insert into comuna")) s"$trimmed ON CONFLICT (id) DO NOTHING"
    else trimmed
  }
}
