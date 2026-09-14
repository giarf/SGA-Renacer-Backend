package cl.familiarenacer.sga.repositorios

import java.sql.{Connection, ResultSet}
import play.api.libs.json._
import scala.util.Using

case class ApoderadoError(status: Int, mensaje: String) extends RuntimeException(mensaje)

class ApoderadoRepository(openConnection: () => Connection = () => DB.ctx.dataSource.getConnection) {
  private def transaction[A](f: Connection => A): A = Using.resource(openConnection()) { c =>
    c.setAutoCommit(false)
    try { val result = f(c); c.commit(); result }
    catch { case e: Exception => c.rollback(); throw e }
  }
  private def query[A](c: Connection, sql: String, args: Any*)(read: ResultSet => A): List[A] =
    Using.resource(c.prepareStatement(sql)) { ps =>
      args.zipWithIndex.foreach { case (arg, i) => ps.setObject(i + 1, arg) }
      Using.resource(ps.executeQuery()) { rs =>
        val result = scala.collection.mutable.ListBuffer.empty[A]
        while (rs.next()) result += read(rs)
        result.toList
      }
    }
  private def execute(c: Connection, sql: String, args: Any*): Int = Using.resource(c.prepareStatement(sql)) { ps =>
    args.zipWithIndex.foreach { case (arg, i) => ps.setObject(i + 1, arg) }
    ps.executeUpdate()
  }
  private def requirePersona(c: Connection, id: Int): Unit = {
    if (query(c, "SELECT entidad_id FROM persona_natural WHERE entidad_id = ? FOR UPDATE", id)(_.getInt(1)).isEmpty)
      throw ApoderadoError(404, "La persona no existe.")
  }
  def listar(id: Int, inverso: Boolean = false): List[JsObject] = transaction { c =>
    requirePersona(c, id)
    val target = if (inverso) "r.persona_id" else "r.apoderado_id"
    val filter = if (inverso) "r.apoderado_id" else "r.persona_id"
    query(c, s"""SELECT r.*,p.nombres,p.apellidos,e.telefono,e.rut FROM persona_apoderado r
      JOIN persona_natural p ON p.entidad_id=$target JOIN entidad e ON e.id=p.entidad_id
      WHERE $filter=? ORDER BY r.es_contacto_principal DESC,p.nombres,p.entidad_id""", id) { rs =>
      Json.obj("personaId" -> rs.getInt("persona_id"), "apoderadoId" -> rs.getInt("apoderado_id"),
        "nombreCompleto" -> (rs.getString("nombres") + " " + Option(rs.getString("apellidos")).getOrElse("")).trim,
        "telefono" -> Option(rs.getString("telefono")), "rut" -> Option(rs.getString("rut")),
        "parentesco" -> rs.getString("parentesco"), "esContactoPrincipal" -> rs.getBoolean("es_contacto_principal"),
        "observaciones" -> rs.getString("observaciones"))
    }
  }
  def guardar(persona: Int, apoderado: Int, parentesco: String, principal: Boolean, observaciones: String): Unit = {
    if (persona <= 0 || apoderado <= 0 || persona == apoderado) throw ApoderadoError(400, "Selecciona dos personas distintas.")
    if (!Set("Madre", "Padre", "Abuela", "Abuelo", "Tutor/a", "Otro").contains(parentesco)) throw ApoderadoError(400, "Parentesco inválido.")
    if (observaciones.length > 2000) throw ApoderadoError(400, "Las observaciones admiten hasta 2000 caracteres.")
    transaction { c =>
      // Serializa cambios del contacto principal y bloquea personas en orden estable.
      List(persona, apoderado).sorted.foreach(requirePersona(c, _))
      if (principal) execute(c, "UPDATE persona_apoderado SET es_contacto_principal=FALSE WHERE persona_id=? AND es_contacto_principal", persona)
      execute(c, """INSERT INTO persona_apoderado(persona_id,apoderado_id,parentesco,es_contacto_principal,observaciones)
        VALUES(?,?,?,?,?) ON CONFLICT(persona_id,apoderado_id) DO UPDATE SET parentesco=EXCLUDED.parentesco,
        es_contacto_principal=EXCLUDED.es_contacto_principal,observaciones=EXCLUDED.observaciones""",
        persona, apoderado, parentesco, principal, observaciones.trim)
      ()
    }
  }
  def quitar(persona: Int, apoderado: Int): Unit = transaction { c =>
    List(persona, apoderado).sorted.foreach(requirePersona(c, _))
    if (execute(c, "DELETE FROM persona_apoderado WHERE persona_id=? AND apoderado_id=?", persona, apoderado) == 0)
      throw ApoderadoError(404, "El vínculo ya no existe.")
  }
}
