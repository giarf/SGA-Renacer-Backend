package cl.familiarenacer.sga.repositorios

import cl.familiarenacer.sga.modelos._
import java.sql.{Connection, PreparedStatement, ResultSet}
import play.api.libs.json._
import scala.collection.mutable.ListBuffer
import scala.util.Using

class CampanaRepository(openConnection: () => Connection = () => DB.ctx.dataSource.getConnection) {
  private def connection[A](f: Connection => A): A = Using.resource(openConnection())(f)
  private def transaction[A](f: Connection => A): A = connection { c =>
    c.setAutoCommit(false)
    try { val result = f(c); c.commit(); result }
    catch { case e: Exception => c.rollback(); throw e }
  }
  private def bind(ps: PreparedStatement, args: Seq[Any]): Unit =
    args.zipWithIndex.foreach { case (arg, i) => ps.setObject(i + 1, arg) }
  private def query[A](c: Connection, sql: String, args: Any*)(read: ResultSet => A): List[A] =
    Using.resource(c.prepareStatement(sql)) { ps =>
      bind(ps, args)
      Using.resource(ps.executeQuery()) { rs =>
        val result = ListBuffer.empty[A]
        while (rs.next()) result += read(rs)
        result.toList
      }
    }
  private def execute(c: Connection, sql: String, args: Any*): Int =
    Using.resource(c.prepareStatement(sql)) { ps => bind(ps, args); ps.executeUpdate() }

  def asegurarEsquema(): Unit = transaction { c =>
    val stream = Option(getClass.getResourceAsStream("/campanas.sql")).getOrElse(throw new IllegalStateException("Falta campanas.sql"))
    val sql = Using.resource(scala.io.Source.fromInputStream(stream))(_.mkString)
    Using.resource(c.createStatement())(_.execute(sql)); ()
  }
  private val select = """SELECT c.*,
    (SELECT COUNT(*) FROM campana_participante p WHERE p.campana_id = c.id) AS total,
    (SELECT COUNT(*) FROM campana_participante p WHERE p.campana_id = c.id AND p.padrino_id IS NULL) AS sin_padrino
    FROM campana c"""
  private def readCampana(rs: ResultSet): Campana = Campana(rs.getInt("id"), rs.getString("nombre"), rs.getDate("fecha").toLocalDate,
    rs.getString("descripcion"), rs.getString("estado"), rs.getInt("version"), rs.getInt("total"), rs.getInt("sin_padrino"))
  def listar(): List[Campana] = connection(c => query(c, select + " ORDER BY c.fecha DESC, c.id DESC")(readCampana))

  def crear(body: CrearCampana): Int = {
    val nombre = AsistenciaValidacion.nombre(body.nombre)
    if (body.descripcion.length > 2000) throw CampanaError(400, "La descripción admite hasta 2000 caracteres.")
    if (!Set("apadrinamiento", "personalizada").contains(body.plantilla)) throw CampanaError(400, "Plantilla inválida.")
    transaction { c =>
      val id = query(c, "INSERT INTO campana(nombre, fecha, descripcion) VALUES (?, ?, ?) RETURNING id", nombre, body.fecha, body.descripcion.trim)(_.getInt(1)).head
      if (body.plantilla == "apadrinamiento") {
        val columnas = Seq(
          ("Gustos y preferencias", "text", "gustos"), ("Ficha enviada", "boolean", "ficha_enviada"),
          ("Regalo recibido", "boolean", "recibido"), ("Código / ubicación", "text", "ubicacion"),
          ("Regalo organizado", "boolean", "organizado"), ("Regalo entregado", "boolean", "entregado"),
          ("Recibido por", "text", "recibido_por"), ("Dibujo recibido", "boolean", "dibujo"),
          ("Agradecimiento enviado por WhatsApp", "boolean", "agradecimiento"), ("Observaciones", "text", "observaciones"))
        columnas.foreach { case (n, t, k) => execute(c, "INSERT INTO campana_columna(campana_id, nombre, tipo, clave) VALUES (?, ?, ?, ?)", id, n, t, k) }
      }
      id
    }
  }

  // Shared campaign locks serialize closure against edits while allowing independent cells to be saved together.
  private def editable(c: Connection, id: Int): Unit = {
    val estado = query(c, "SELECT estado FROM campana WHERE id = ? FOR SHARE", id)(_.getString(1)).headOption
      .getOrElse(throw CampanaError(404, "La campaña ya no existe."))
    if (estado == "cerrada") throw CampanaError(409, "La campaña está cerrada. Reábrela para editar.")
  }
  private def version(v: Int): Unit = if (v < 0 || v == Int.MaxValue) throw CampanaError(400, "Versión inválida.")
  private def conflict(): Nothing = throw CampanaError(409, "Otra persona modificó este registro. Actualiza y revisa los datos antes de guardar.")

  def cambiarEstado(id: Int, body: CambiarEstadoCampana): Unit = transaction { c =>
    version(body.version)
    if (!Set("borrador", "activa", "cerrada").contains(body.estado)) throw CampanaError(400, "Estado inválido.")
    if (query(c, "SELECT id FROM campana WHERE id = ? FOR UPDATE", id)(_.getInt(1)).isEmpty) throw CampanaError(404, "La campaña ya no existe.")
    if (execute(c, "UPDATE campana SET estado = ?, version = version + 1 WHERE id = ? AND version = ?", body.estado, id, body.version) == 0) conflict()
  }

  def detalle(id: Int): DetalleCampana = transaction { c =>
    c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ)
    val campana = query(c, select + " WHERE c.id = ?", id)(readCampana).headOption.getOrElse(throw CampanaError(404, "La campaña ya no existe."))
    val columnas = query(c, "SELECT * FROM campana_columna WHERE campana_id = ? ORDER BY id", id) { rs =>
      ColumnaCampana(rs.getInt("id"), rs.getString("nombre"), rs.getString("tipo"), Option(rs.getString("clave")), rs.getString("mensaje"), rs.getString("destinatario"), rs.getInt("version"))
    }
    val valores = query(c, "SELECT * FROM campana_valor WHERE campana_id = ?", id) { rs =>
      (rs.getInt("participante_id"), rs.getInt("columna_id").toString,
        ValorCampana(Json.parse(rs.getString("valor")), rs.getInt("version"), rs.getTimestamp("actualizado_en").toInstant.toString))
    }.groupBy(_._1).view.mapValues(_.map(v => v._2 -> v._3).toMap).toMap
    val participantes = query(c, """SELECT p.*, b.nombres AS b_nombre, b.apellidos AS b_apellido, eb.telefono AS b_telefono,
      a.nombres AS a_nombre, a.apellidos AS a_apellido, ea.telefono AS a_telefono
      FROM campana_participante p JOIN persona_natural b ON b.entidad_id = p.beneficiario_id
      JOIN entidad eb ON eb.id = b.entidad_id LEFT JOIN persona_natural a ON a.entidad_id = p.padrino_id
      LEFT JOIN entidad ea ON ea.id = a.entidad_id WHERE p.campana_id = ? ORDER BY b.nombres, b.apellidos, p.id""", id) { rs =>
      def contacto(prefix: String, personaId: Int) = ContactoCampana(personaId,
        (rs.getString(prefix + "_nombre") + " " + Option(rs.getString(prefix + "_apellido")).getOrElse("")).trim, Option(rs.getString(prefix + "_telefono")))
      val rowId = rs.getInt("id")
      val padrino = Option(rs.getObject("padrino_id")).map(_ => contacto("a", rs.getInt("padrino_id")))
      ParticipanteCampana(rowId, contacto("b", rs.getInt("beneficiario_id")), padrino, rs.getInt("version"), valores.getOrElse(rowId, Map.empty))
    }
    DetalleCampana(campana, columnas, participantes)
  }

  def agregarParticipante(id: Int, body: RegistrarParticipante): Int = transaction { c =>
    editable(c, id)
    query(c, "INSERT INTO campana_participante(campana_id, beneficiario_id) VALUES (?, ?) RETURNING id", id, body.beneficiarioId)(_.getInt(1)).head
  }
  def asignarPadrino(id: Int, participanteId: Int, body: AsignarPadrino): Unit = transaction { c =>
    version(body.version); editable(c, id)
    val actual = query(c, "SELECT beneficiario_id, padrino_id, version FROM campana_participante WHERE campana_id = ? AND id = ? FOR UPDATE", id, participanteId) { rs =>
      (rs.getInt(1), Option(rs.getObject(2)).map(_ => rs.getInt(2)), rs.getInt(3))
    }.headOption.getOrElse(throw CampanaError(404, "El participante ya no existe."))
    if (actual._3 != body.version) conflict()
    if (body.padrinoId.contains(actual._1)) throw CampanaError(400, "El beneficiario y el padrino deben ser personas diferentes.")
    if (actual._2 != body.padrinoId) {
      execute(c, "UPDATE campana_participante SET padrino_id = ?, version = version + 1 WHERE campana_id = ? AND id = ?", body.padrinoId.map(Int.box).orNull, id, participanteId)
      execute(c, """UPDATE campana_valor SET valor = jsonb_set(valor, '{enviado}', 'false'::jsonb), version = version + 1, actualizado_en = CURRENT_TIMESTAMP
        WHERE campana_id = ? AND participante_id = ? AND columna_id IN
        (SELECT id FROM campana_columna WHERE campana_id = ? AND tipo = 'whatsapp' AND destinatario = 'colaborador')""", id, participanteId, id)
      // Communications belong to the assigned sponsor; reset them (without resetting versions) on reassignment.
      execute(c, """UPDATE campana_valor SET valor = 'false'::jsonb, version = version + 1, actualizado_en = CURRENT_TIMESTAMP
        WHERE campana_id = ? AND participante_id = ? AND columna_id IN
        (SELECT id FROM campana_columna WHERE campana_id = ? AND clave IN ('ficha_enviada', 'agradecimiento'))""", id, participanteId, id)
    }
  }
  def quitarParticipante(id: Int, participanteId: Int): Unit = transaction { c =>
    editable(c, id)
    if (execute(c, "DELETE FROM campana_participante WHERE campana_id = ? AND id = ?", id, participanteId) == 0) throw CampanaError(404, "El participante ya no existe.")
  }
  def agregarColumna(id: Int, body: CrearColumnaAsistencia): Int = {
    val nombre = AsistenciaValidacion.nombre(body.nombre); val tipo = if (body.tipo == "whatsapp") body.tipo else AsistenciaValidacion.tipo(body.tipo)
    transaction { c =>
      editable(c, id)
      query(c, "INSERT INTO campana_columna(campana_id, nombre, tipo) VALUES (?, ?, ?) RETURNING id", id, nombre, tipo)(_.getInt(1)).head
    }
  }
  def configurarMensaje(id: Int, columnaId: Int, body: ConfigurarMensajeCampana): Unit = transaction { c =>
    version(body.version); editable(c, id)
    if (body.mensaje.length > 2000 || !Set("beneficiario", "colaborador").contains(body.destinatario))
      throw CampanaError(400, "Revisa el destinatario y el mensaje (máximo 2000 caracteres).")
    val actual = query(c, "SELECT tipo, version, mensaje, destinatario FROM campana_columna WHERE campana_id = ? AND id = ? FOR UPDATE", id, columnaId) { rs =>
      (rs.getString(1), rs.getInt(2), rs.getString(3), rs.getString(4))
    }.headOption.getOrElse(throw CampanaError(404, "La columna ya no existe."))
    if (actual._1 != "whatsapp") throw CampanaError(400, "Esta columna no es de WhatsApp.")
    if (actual._2 != body.version) conflict()
    if (actual._3 != body.mensaje || actual._4 != body.destinatario) {
      execute(c, "UPDATE campana_columna SET mensaje = ?, destinatario = ?, version = version + 1 WHERE campana_id = ? AND id = ?", body.mensaje, body.destinatario, id, columnaId)
      execute(c, "UPDATE campana_valor SET valor = ?::jsonb, version = version + 1, actualizado_en = CURRENT_TIMESTAMP WHERE campana_id = ? AND columna_id = ?",
        Json.stringify(Json.obj("texto" -> body.mensaje, "enviado" -> false)), id, columnaId)
    }
  }
  def guardarValor(id: Int, participanteId: Int, columnaId: Int, body: GuardarValorAsistencia): ValorCampana =
    guardarCelda(id, participanteId, columnaId, GuardarCeldaCampana(body.valor, body.version))

  def guardarCelda(id: Int, participanteId: Int, columnaId: Int, body: GuardarCeldaCampana): ValorCampana = transaction { c =>
    version(body.version); editable(c, id)
    val columna = query(c, "SELECT tipo, clave, mensaje, destinatario, version FROM campana_columna WHERE campana_id = ? AND id = ? FOR SHARE", id, columnaId)(rs => (rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getInt(5)))
      .headOption.getOrElse(throw CampanaError(404, "La columna ya no existe."))
    if (columna._1 == "whatsapp") CampanaMensaje.validar(body.valor) else AsistenciaValidacion.valor(columna._1, body.valor)
    if (columna._1 == "whatsapp") {
      if (!body.columnaVersion.contains(columna._5)) conflict()
      if ((body.valor \ "texto").as[String] != columna._3) throw CampanaError(409, "El mensaje cambió. Actualiza la campaña.")
    }
    val padrino = query(c, "SELECT padrino_id FROM campana_participante WHERE campana_id = ? AND id = ? FOR SHARE", id, participanteId)(rs => Option(rs.getObject(1)))
      .headOption.getOrElse(throw CampanaError(404, "El participante ya no existe."))
    if (Set("ficha_enviada", "agradecimiento").contains(columna._2) && body.valor == JsBoolean(true) && padrino.isEmpty)
      throw CampanaError(400, "Asigna un padrino antes de marcar el envío.")
    if (columna._1 == "whatsapp" && columna._4 == "colaborador" && (body.valor \ "enviado").as[Boolean] && padrino.isEmpty)
      throw CampanaError(400, "Asigna un padrino antes de marcar el envío.")
    val result = if (body.version == 0) query(c, """INSERT INTO campana_valor(campana_id, participante_id, columna_id, valor, version)
      VALUES (?, ?, ?, ?::jsonb, 1) ON CONFLICT DO NOTHING RETURNING version, actualizado_en""", id, participanteId, columnaId, Json.stringify(body.valor))(readValor(body.valor))
    else query(c, """UPDATE campana_valor SET valor = ?::jsonb, version = version + 1, actualizado_en = CURRENT_TIMESTAMP
      WHERE campana_id = ? AND participante_id = ? AND columna_id = ? AND version = ? RETURNING version, actualizado_en""",
      Json.stringify(body.valor), id, participanteId, columnaId, body.version)(readValor(body.valor))
    result.headOption.getOrElse(conflict())
  }
  private def readValor(valor: JsValue)(rs: ResultSet): ValorCampana =
    ValorCampana(valor, rs.getInt("version"), rs.getTimestamp("actualizado_en").toInstant.toString)
}
