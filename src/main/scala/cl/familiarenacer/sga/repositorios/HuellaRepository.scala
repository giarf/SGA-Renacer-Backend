package cl.familiarenacer.sga.repositorios

import cl.familiarenacer.sga.huellero.HuellaMatcher
import cl.familiarenacer.sga.modelos._
import java.sql.{Connection, ResultSet}
import scala.collection.mutable.ListBuffer
import scala.util.Using

class HuellaRepository(openConnection: () => Connection = () => DB.ctx.dataSource.getConnection) {
  private def connection[A](f: Connection => A): A = Using.resource(openConnection())(f)

  private def query[A](c: Connection, sql: String, args: Any*)(read: ResultSet => A): List[A] =
    Using.resource(c.prepareStatement(sql)) { ps =>
      args.zipWithIndex.foreach { case (arg, i) => ps.setObject(i + 1, arg) }
      Using.resource(ps.executeQuery()) { rs =>
        val result = ListBuffer.empty[A]
        while (rs.next()) result += read(rs)
        result.toList
      }
    }

  private def readHuella(rs: ResultSet) = HuellaRegistrada(rs.getInt("id"), rs.getInt("persona_id"), rs.getString("dedo"),
    rs.getString("formato"), Option(rs.getObject("calidad")).map(_ => rs.getInt("calidad")), rs.getTimestamp("registrada").toInstant.toString)

  def listar(personaId: Int): List[HuellaRegistrada] = connection { c =>
    if (query(c, "SELECT 1 FROM persona_natural WHERE entidad_id = ?", personaId)(_.getInt(1)).isEmpty)
      throw HuellaError(404, "La persona no existe.")
    query(c, "SELECT id, persona_id, dedo, formato, calidad, registrada FROM persona_huella WHERE persona_id = ? ORDER BY id", personaId)(readHuella)
  }

  private def existePersona(c: Connection, personaId: Int): Unit =
    if (query(c, "SELECT 1 FROM persona_natural WHERE entidad_id = ?", personaId)(_.getInt(1)).isEmpty)
      throw HuellaError(404, "La persona no existe.")

  /** Reserva la fila del dedo (persona, dedo) para obtener su huella_id. Devuelve (id, creada). */
  def reservar(personaId: Int, dedo: String): (Int, Boolean) = connection { c =>
    existePersona(c, personaId)
    query(c, """INSERT INTO persona_huella(persona_id, dedo, formato) VALUES (?, ?, ?)
      ON CONFLICT (persona_id, dedo) DO UPDATE SET dedo = EXCLUDED.dedo
      RETURNING id, (xmax = 0) AS creada""", personaId, dedo, HuellaMatcher.Formato)(rs => (rs.getInt(1), rs.getBoolean(2))).head
  }

  /** Reemplaza todas las tomas del dedo en una transacción; calidad = menor número de minucias. */
  def guardarTomas(huellaId: Int, tomas: Seq[(Int, Array[Byte], Int)]): HuellaRegistrada = connection { c =>
    c.setAutoCommit(false)
    try {
      Using.resource(c.prepareStatement("DELETE FROM persona_huella_toma WHERE huella_id = ?")) { ps => ps.setInt(1, huellaId); ps.executeUpdate() }
      Using.resource(c.prepareStatement("INSERT INTO persona_huella_toma(huella_id, toma, plantilla, minucias) VALUES (?, ?, ?, ?)")) { ps =>
        tomas.foreach { case (n, bytes, minucias) =>
          ps.setInt(1, huellaId); ps.setInt(2, n); ps.setBytes(3, bytes); ps.setInt(4, minucias); ps.addBatch()
        }
        ps.executeBatch()
      }
      val huella = query(c, """UPDATE persona_huella SET formato = ?, calidad = ?, registrada = CURRENT_TIMESTAMP WHERE id = ?
        RETURNING id, persona_id, dedo, formato, calidad, registrada""",
        HuellaMatcher.Formato, Int.box(tomas.map(_._3).min), huellaId)(readHuella).headOption
        .getOrElse(throw HuellaError(404, "La huella ya no existe."))
      c.commit()
      huella
    } catch { case e: Throwable => c.rollback(); throw e }
    finally c.setAutoCommit(true)
  }

  /** Borra la fila reservada si nunca llegó a tener tomas (registro fallido de un dedo nuevo). */
  def descartarSinTomas(huellaId: Int): Unit = connection { c =>
    Using.resource(c.prepareStatement("DELETE FROM persona_huella h WHERE id = ? AND NOT EXISTS (SELECT 1 FROM persona_huella_toma t WHERE t.huella_id = h.id)")) { ps =>
      ps.setInt(1, huellaId); ps.executeUpdate()
    }
  }

  def eliminar(personaId: Int, huellaId: Int): Unit = connection { c =>
    Using.resource(c.prepareStatement("DELETE FROM persona_huella WHERE persona_id = ? AND id = ?")) { ps =>
      ps.setInt(1, personaId); ps.setInt(2, huellaId)
      if (ps.executeUpdate() == 0) throw HuellaError(404, "La huella ya no existe.")
    }
  }

  def tomas(): List[TomaGuardada] = connection { c =>
    query(c, """SELECT t.huella_id, h.persona_id, t.toma, t.plantilla, t.minucias FROM persona_huella_toma t
      JOIN persona_huella h ON h.id = t.huella_id ORDER BY t.huella_id, t.toma""") { rs =>
      TomaGuardada(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getBytes(4), rs.getInt(5))
    }
  }

  def tomasDe(huellaId: Int): List[TomaGuardada] = tomas().filter(_.huellaId == huellaId)

  def huellas(): List[(Int, Int)] = connection { c => query(c, "SELECT id, persona_id FROM persona_huella ORDER BY id")(rs => (rs.getInt(1), rs.getInt(2))) }

  def persona(huellaId: Int): Option[PersonaIdentificada] = connection { c =>
    query(c, """SELECT p.entidad_id, p.nombres, p.apellidos, e.rut, h.dedo FROM persona_huella h
      JOIN persona_natural p ON p.entidad_id = h.persona_id JOIN entidad e ON e.id = p.entidad_id WHERE h.id = ?""", huellaId) { rs =>
      PersonaIdentificada(rs.getInt(1), (rs.getString(2) + " " + Option(rs.getString(3)).getOrElse("")).trim, Option(rs.getString(4)), rs.getString(5))
    }.headOption
  }
}
