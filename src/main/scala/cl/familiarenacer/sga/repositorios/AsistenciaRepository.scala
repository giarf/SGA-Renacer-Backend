package cl.familiarenacer.sga.repositorios

import cl.familiarenacer.sga.modelos._
import java.sql.{Connection, PreparedStatement, ResultSet}
import play.api.libs.json._
import scala.collection.mutable.ListBuffer
import scala.util.Using

class AsistenciaRepository(openConnection: () => Connection = () => DB.ctx.dataSource.getConnection) {
  private def connection[A](f: Connection => A): A = Using.resource(openConnection())(f)

  private def transaction[A](f: Connection => A): A = connection { conn =>
    conn.setAutoCommit(false)
    try { val result = f(conn); conn.commit(); result }
    catch { case e: Exception => conn.rollback(); throw e }
  }

  private def query[A](conn: Connection, sql: String, args: Any*)(read: ResultSet => A): List[A] =
    Using.resource(conn.prepareStatement(sql)) { ps =>
      bind(ps, args)
      Using.resource(ps.executeQuery()) { rs =>
        val result = ListBuffer.empty[A]
        while (rs.next()) result += read(rs)
        result.toList
      }
    }

  private def bind(ps: PreparedStatement, args: Seq[Any]): Unit =
    args.zipWithIndex.foreach { case (arg, index) => ps.setObject(index + 1, arg) }

  private def execute(conn: Connection, sql: String, args: Any*): Int =
    Using.resource(conn.prepareStatement(sql)) { ps => bind(ps, args); ps.executeUpdate() }

  def asegurarEsquema(): Unit = transaction { conn =>
    val stream = Option(getClass.getResourceAsStream("/asistencia.sql")).getOrElse(throw new IllegalStateException("Falta asistencia.sql"))
    val sql = Using.resource(scala.io.Source.fromInputStream(stream))(_.mkString)
    Using.resource(conn.createStatement())(_.execute(sql))
    ()
  }

  private val eventoSelect = """SELECT e.id, e.nombre, e.fecha, e.descripcion,
    (SELECT COUNT(*) FROM asistencia_persona a WHERE a.evento_id = e.id) AS total
    FROM asistencia_evento e"""

  private def readEvento(rs: ResultSet): EventoAsistencia = EventoAsistencia(
    rs.getInt("id"), rs.getString("nombre"), rs.getDate("fecha").toLocalDate, rs.getString("descripcion"), rs.getInt("total")
  )

  def listar(): List[EventoAsistencia] = connection(conn => query(conn, eventoSelect + " ORDER BY e.fecha DESC, e.id DESC")(readEvento))

  def crear(body: CrearEventoAsistencia): Int = {
    val nombre = AsistenciaValidacion.nombre(body.nombre)
    if (body.descripcion.length > 2000) throw AsistenciaError(400, "La descripción admite hasta 2000 caracteres.")
    connection(conn => query(conn, "INSERT INTO asistencia_evento(nombre, fecha, descripcion) VALUES (?, ?, ?) RETURNING id",
      nombre, body.fecha, body.descripcion.trim)(_.getInt(1)).head)
  }

  def eliminar(eventoId: Int): Unit = connection { conn =>
    if (execute(conn, "DELETE FROM asistencia_evento WHERE id = ?", eventoId) == 0) throw AsistenciaError(404, "El evento ya no existe.")
  }

  def detalle(eventoId: Int): DetalleAsistencia = transaction { conn =>
    // All parts of a refresh describe the same committed snapshot.
    conn.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ)
    val evento = query(conn, eventoSelect + " WHERE e.id = ?", eventoId)(readEvento).headOption
      .getOrElse(throw AsistenciaError(404, "El evento ya no existe."))
    val columnas = query(conn, "SELECT id, nombre, tipo FROM asistencia_columna WHERE evento_id = ? ORDER BY id", eventoId) { rs =>
      ColumnaAsistencia(rs.getInt("id"), rs.getString("nombre"), rs.getString("tipo"))
    }
    val valores = query(conn, "SELECT asistencia_id, columna_id, valor::text, version FROM asistencia_valor WHERE evento_id = ?", eventoId) { rs =>
      (rs.getInt("asistencia_id"), rs.getInt("columna_id").toString, ValorAsistencia(Json.parse(rs.getString("valor")), rs.getInt("version")))
    }.groupBy(_._1).view.mapValues(_.map(v => v._2 -> v._3).toMap).toMap
    val asistentes = query(conn, """SELECT a.id, a.persona_id, a.llegada, p.nombres, p.apellidos, e.rut
      FROM asistencia_persona a JOIN persona_natural p ON p.entidad_id = a.persona_id
      JOIN entidad e ON e.id = p.entidad_id WHERE a.evento_id = ? ORDER BY a.llegada DESC, a.id DESC""", eventoId) { rs =>
      val id = rs.getInt("id")
      PersonaAsistente(id, rs.getInt("persona_id"), (rs.getString("nombres") + " " + Option(rs.getString("apellidos")).getOrElse("")).trim,
        Option(rs.getString("rut")), rs.getTimestamp("llegada").toInstant.toString, valores.getOrElse(id, Map.empty))
    }
    DetalleAsistencia(evento, columnas, asistentes)
  }

  def agregarPersona(eventoId: Int, personaId: Int): (Int, Boolean) = transaction { conn =>
    val inserted = query(conn, """INSERT INTO asistencia_persona(evento_id, persona_id) VALUES (?, ?)
      ON CONFLICT (evento_id, persona_id) DO NOTHING RETURNING id""", eventoId, personaId)(_.getInt(1)).headOption
    inserted match {
      case Some(id) => (id, true)
      case None =>
        val id = query(conn, "SELECT id FROM asistencia_persona WHERE evento_id = ? AND persona_id = ?", eventoId, personaId)(_.getInt(1))
          .headOption.getOrElse(throw AsistenciaError(409, "La asistencia cambió. Actualiza e intenta nuevamente."))
        (id, false)
    }
  }

  def quitarPersona(eventoId: Int, asistenciaId: Int): Unit = connection { conn =>
    if (execute(conn, "DELETE FROM asistencia_persona WHERE evento_id = ? AND id = ?", eventoId, asistenciaId) == 0)
      throw AsistenciaError(404, "Esta persona ya no está en la asistencia.")
  }

  def agregarColumna(eventoId: Int, body: CrearColumnaAsistencia): Int = {
    val nombre = AsistenciaValidacion.nombre(body.nombre)
    val tipo = AsistenciaValidacion.tipo(body.tipo)
    connection(conn => query(conn, "INSERT INTO asistencia_columna(evento_id, nombre, tipo) VALUES (?, ?, ?) RETURNING id",
      eventoId, nombre, tipo)(_.getInt(1)).head)
  }

  def quitarColumna(eventoId: Int, columnaId: Int): Unit = connection { conn =>
    if (execute(conn, "DELETE FROM asistencia_columna WHERE evento_id = ? AND id = ?", eventoId, columnaId) == 0)
      throw AsistenciaError(404, "La columna ya no existe.")
  }

  def guardarValor(eventoId: Int, asistenciaId: Int, columnaId: Int, body: GuardarValorAsistencia): ValorAsistencia = transaction { conn =>
    if (body.version < 0 || body.version == Int.MaxValue) throw AsistenciaError(400, "Versión inválida.")
    val tipo = query(conn, "SELECT tipo FROM asistencia_columna WHERE evento_id = ? AND id = ? FOR SHARE", eventoId, columnaId)(_.getString(1))
      .headOption.getOrElse(throw AsistenciaError(404, "La columna ya no existe."))
    AsistenciaValidacion.valor(tipo, body.valor)
    if (query(conn, "SELECT id FROM asistencia_persona WHERE evento_id = ? AND id = ? FOR SHARE", eventoId, asistenciaId)(_.getInt(1)).isEmpty)
      throw AsistenciaError(404, "Esta persona ya no está en la asistencia.")
    // Compare-and-set per cell: unrelated edits merge, stale edits never silently overwrite.
    val versions = if (body.version == 0) {
      query(conn, """INSERT INTO asistencia_valor(evento_id, asistencia_id, columna_id, valor, version)
        VALUES (?, ?, ?, ?::jsonb, 1) ON CONFLICT DO NOTHING RETURNING version""",
        eventoId, asistenciaId, columnaId, Json.stringify(body.valor))(_.getInt(1))
    } else {
      query(conn, """UPDATE asistencia_valor SET valor = ?::jsonb, version = version + 1
        WHERE evento_id = ? AND asistencia_id = ? AND columna_id = ? AND version = ? RETURNING version""",
        Json.stringify(body.valor), eventoId, asistenciaId, columnaId, body.version)(_.getInt(1))
    }
    ValorAsistencia(body.valor, versions.headOption.getOrElse(throw AsistenciaError(409,
      "Otra persona modificó esta casilla. Revisa el valor actualizado antes de volver a guardar.")))
  }
}
