package cl.familiarenacer.sga.api.routes

import cl.familiarenacer.sga.api.ApiSupport
import cl.familiarenacer.sga.modelos._
import cl.familiarenacer.sga.repositorios.EgresoRepository
import play.api.libs.json._

import java.time.LocalDate

class EgresosRoutes(egresoRepo: EgresoRepository)(implicit cc: castor.Context, log: cask.Logger) extends cask.Routes with ApiSupport {

  // ===== DTOs =====

  case class CrearEgresoRequest(
    egreso: EgresoRecurso,
    pecuniario: Option[EgresoPecuniario] = None,
    detalles: List[DetalleEgresoRecurso] = Nil
  )
  implicit val crearEgresoFormat: OFormat[CrearEgresoRequest] = Json.using[Json.WithDefaultValues].format[CrearEgresoRequest]

  case class ActualizarEgresoRequest(
    egreso: EgresoRecurso,
    pecuniario: Option[EgresoPecuniario] = None
  )
  implicit val actualizarEgresoFormat: OFormat[ActualizarEgresoRequest] = Json.using[Json.WithDefaultValues].format[ActualizarEgresoRequest]

  // ===== ENDPOINTS =====

  @cask.options("/api/egresos")
  def egresosOptions() = corsOptions()

  @cask.options("/api/egresos/:id")
  def egresoByIdOptions(id: Int) = corsOptions()

  @cask.post("/api/egresos")
  def crearEgreso(request: cask.Request) = {
    try {
      val body = Json.parse(request.text()).as[CrearEgresoRequest]
      val egresoNormalizado = normalizarEgreso(body.egreso)
      val id = egresoRepo.crearEgreso(egresoNormalizado, body.pecuniario, body.detalles)
      respond(Json.obj("id" -> id, "mensaje" -> "Egreso registrado exitosamente"), 201)
    } catch {
      case e: IllegalArgumentException =>
        respond(Json.obj("error" -> e.getMessage), 400)
      case e: org.postgresql.util.PSQLException if e.getSQLState == "23503" =>
        respond(Json.obj("error" -> "Referencia inválida: cuenta, destino o ítem no existe"), 409)
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  @cask.get("/api/egresos")
  def listarEgresos(tipo_egreso: String = "", destino_entidad_id: String = "") = {
    try {
      val tipoOpt = Option(tipo_egreso).map(_.trim).filter(_.nonEmpty)
      val destinoOpt = Option(destino_entidad_id).map(_.trim).filter(_.nonEmpty).map(_.toInt)
      val egresos = egresoRepo.listarEgresos(tipoOpt, destinoOpt)
      respond(Json.toJson(egresos))
    } catch {
      case _: NumberFormatException =>
        respond(Json.obj("error" -> "destino_entidad_id debe ser numérico"), 400)
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  @cask.get("/api/egresos/:id")
  def obtenerEgreso(id: Int) = {
    try {
      egresoRepo.obtenerEgresoDetalle(id) match {
        case Some(egresoDetalle) => respond(Json.toJson(egresoDetalle))
        case None => respond(Json.obj("error" -> s"Egreso con ID $id no encontrado"), 404)
      }
    } catch {
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  @cask.put("/api/egresos/:id")
  def actualizarEgreso(id: Int, request: cask.Request) = {
    try {
      val body = Json.parse(request.text()).as[ActualizarEgresoRequest]
      val filas = egresoRepo.actualizarEgreso(id, body.egreso, body.pecuniario)
      if (filas > 0) {
        respond(Json.obj("mensaje" -> "Egreso actualizado exitosamente"))
      } else {
        respond(Json.obj("error" -> s"Egreso con ID $id no encontrado"), 404)
      }
    } catch {
      case e: NoSuchElementException =>
        respond(Json.obj("error" -> e.getMessage), 404)
      case e: IllegalArgumentException =>
        respond(Json.obj("error" -> e.getMessage), 400)
      case e: org.postgresql.util.PSQLException if e.getSQLState == "23503" =>
        respond(Json.obj("error" -> "Referencia inválida al actualizar egreso"), 409)
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  @cask.delete("/api/egresos/:id")
  def eliminarEgreso(id: Int) = {
    try {
      val eliminado = egresoRepo.eliminarEgreso(id)
      if (eliminado) {
        respond(Json.obj("mensaje" -> "Egreso eliminado exitosamente"))
      } else {
        respond(Json.obj("error" -> s"Egreso con ID $id no encontrado"), 404)
      }
    } catch {
      case e: IllegalArgumentException =>
        respond(Json.obj("error" -> e.getMessage), 400)
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  initialize()

  private def normalizarEgreso(egreso: EgresoRecurso): EgresoRecurso = {
    if (egreso.fecha.isDefined) egreso else egreso.copy(fecha = Some(LocalDate.now()))
  }
}
