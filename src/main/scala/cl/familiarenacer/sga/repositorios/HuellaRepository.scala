package cl.familiarenacer.sga.repositorios

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

  /** Reemplaza la huella del mismo dedo si ya existía. */
  def guardar(personaId: Int, dedo: String, captura: CapturaHuella): HuellaRegistrada = connection { c =>
    if (query(c, "SELECT 1 FROM persona_natural WHERE entidad_id = ?", personaId)(_.getInt(1)).isEmpty)
      throw HuellaError(404, "La persona no existe.")
    query(c, """INSERT INTO persona_huella(persona_id, dedo, plantilla, formato, calidad) VALUES (?, ?, ?, ?, ?)
      ON CONFLICT (persona_id, dedo) DO UPDATE SET plantilla = EXCLUDED.plantilla, formato = EXCLUDED.formato,
      calidad = EXCLUDED.calidad, registrada = CURRENT_TIMESTAMP
      RETURNING id, persona_id, dedo, formato, calidad, registrada""",
      personaId, dedo, captura.plantilla, captura.formato, captura.calidad.map(Int.box).orNull)(readHuella).head
  }

  def eliminar(personaId: Int, huellaId: Int): Unit = connection { c =>
    Using.resource(c.prepareStatement("DELETE FROM persona_huella WHERE persona_id = ? AND id = ?")) { ps =>
      ps.setInt(1, personaId); ps.setInt(2, huellaId)
      if (ps.executeUpdate() == 0) throw HuellaError(404, "La huella ya no existe.")
    }
  }

  def plantillas(): List[PlantillaHuella] = connection { c =>
    query(c, "SELECT id, persona_id, plantilla, formato FROM persona_huella ORDER BY id") { rs =>
      PlantillaHuella(rs.getInt("id"), rs.getInt("persona_id"), rs.getString("plantilla"), rs.getString("formato"))
    }
  }

  def persona(huellaId: Int): Option[PersonaIdentificada] = connection { c =>
    query(c, """SELECT p.entidad_id, p.nombres, p.apellidos, e.rut, h.dedo FROM persona_huella h
      JOIN persona_natural p ON p.entidad_id = h.persona_id JOIN entidad e ON e.id = p.entidad_id WHERE h.id = ?""", huellaId) { rs =>
      PersonaIdentificada(rs.getInt(1), (rs.getString(2) + " " + Option(rs.getString(3)).getOrElse("")).trim, Option(rs.getString(4)), rs.getString(5))
    }.headOption
  }
}
