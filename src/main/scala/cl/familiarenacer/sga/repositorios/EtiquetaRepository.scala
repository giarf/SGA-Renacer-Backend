package cl.familiarenacer.sga.repositorios

import cl.familiarenacer.sga.modelos.Etiqueta
import io.getquill._
import java.sql.DriverManager

class EtiquetaRepository(val ctx: PostgresJdbcContext[SnakeCase.type]) {
  import ctx._

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

  def asegurarEsquemaEtiquetas(): Unit = withConnection { conn =>
    val statement = conn.createStatement()
    try {
      statement.execute(
        """
        CREATE TABLE IF NOT EXISTS etiqueta (
          id SERIAL PRIMARY KEY,
          nombre TEXT NOT NULL UNIQUE,
          slug TEXT NOT NULL UNIQUE,
          descripcion TEXT,
          color TEXT,
          activa BOOLEAN NOT NULL DEFAULT TRUE
        )
        """
      )
      statement.execute(
        """
        CREATE TABLE IF NOT EXISTS entidad_etiqueta (
          entidad_id INTEGER NOT NULL REFERENCES entidad(id) ON DELETE CASCADE,
          etiqueta_id INTEGER NOT NULL REFERENCES etiqueta(id) ON DELETE CASCADE,
          created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
          PRIMARY KEY (entidad_id, etiqueta_id)
        )
        """
      )
    } finally statement.close()
  }

  def listarEtiquetas(): List[Etiqueta] = ctx.run(query[Etiqueta].filter(_.activa == true).sortBy(_.nombre))

  def crearEtiqueta(etiqueta: Etiqueta): Long = {
    ctx.run(query[Etiqueta].insertValue(lift(etiqueta)).returningGenerated(_.id)).toLong
  }

  def actualizarEtiqueta(id: Int, etiqueta: Etiqueta): Long = {
    ctx.run(
      query[Etiqueta]
        .filter(_.id == lift(id))
        .update(
          _.nombre -> lift(etiqueta.nombre),
          _.slug -> lift(etiqueta.slug),
          _.descripcion -> lift(etiqueta.descripcion),
          _.color -> lift(etiqueta.color),
          _.activa -> lift(etiqueta.activa)
        )
    )
  }

  def etiquetasPorEntidad(entidadId: Int): List[Etiqueta] = withConnection { conn =>
    val ps = conn.prepareStatement(
      """
      SELECT e.id, e.nombre, e.slug, e.descripcion, e.color, e.activa
      FROM etiqueta e
      JOIN entidad_etiqueta ee ON ee.etiqueta_id = e.id
      WHERE ee.entidad_id = ? AND e.activa = TRUE
      ORDER BY e.nombre
      """
    )
    try {
      ps.setInt(1, entidadId)
      val rs = ps.executeQuery()
      val buffer = scala.collection.mutable.ListBuffer.empty[Etiqueta]
      while (rs.next()) {
        buffer += Etiqueta(
          id = rs.getInt("id"),
          nombre = rs.getString("nombre"),
          slug = rs.getString("slug"),
          descripcion = Option(rs.getString("descripcion")),
          color = Option(rs.getString("color")),
          activa = rs.getBoolean("activa")
        )
      }
      buffer.toList
    } finally ps.close()
  }

  def asignarEtiqueta(entidadId: Int, etiquetaId: Int): Long = asignarEtiquetaMasiva(etiquetaId, List(entidadId))

  def asignarEtiquetaMasiva(etiquetaId: Int, entidadIds: List[Int]): Long = withConnection { conn =>
    if (entidadIds.isEmpty) 0L
    else {
      val ps = conn.prepareStatement("INSERT INTO entidad_etiqueta (entidad_id, etiqueta_id) VALUES (?, ?) ON CONFLICT DO NOTHING")
      try {
        entidadIds.distinct.foreach { entidadId =>
          ps.setInt(1, entidadId)
          ps.setInt(2, etiquetaId)
          ps.addBatch()
        }
        ps.executeBatch().map(_.toLong).sum
      } finally ps.close()
    }
  }

  def quitarEtiqueta(entidadId: Int, etiquetaId: Int): Long = quitarEtiquetaMasiva(etiquetaId, List(entidadId))

  def quitarEtiquetaMasiva(etiquetaId: Int, entidadIds: List[Int]): Long = withConnection { conn =>
    if (entidadIds.isEmpty) 0L
    else {
      val ps = conn.prepareStatement("DELETE FROM entidad_etiqueta WHERE entidad_id = ? AND etiqueta_id = ?")
      try {
        entidadIds.distinct.foreach { entidadId =>
          ps.setInt(1, entidadId)
          ps.setInt(2, etiquetaId)
          ps.addBatch()
        }
        ps.executeBatch().map(_.toLong).sum
      } finally ps.close()
    }
  }
}
