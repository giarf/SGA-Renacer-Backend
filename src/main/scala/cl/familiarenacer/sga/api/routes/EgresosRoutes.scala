package cl.familiarenacer.sga.api.routes

import cl.familiarenacer.sga.api.ApiSupport
import cl.familiarenacer.sga.modelos._
import cl.familiarenacer.sga.repositorios.EgresoRepository
import play.api.libs.json._
import java.time.LocalDate

class EgresosRoutes(egresoRepo: EgresoRepository)(implicit cc: castor.Context, log: cask.Logger) extends cask.Routes with ApiSupport {

  // ===== DTOs =====

  case class RegistrarAyudaSocialRequest(
    egreso: EgresoRecurso,
    ayuda: EgresoAyudaSocial,
    detalles: List[DetalleEgresoRecurso]
  )
  implicit val registrarAyudaSocialFormat: OFormat[RegistrarAyudaSocialRequest] = Json.format[RegistrarAyudaSocialRequest]

  case class RegistrarConsumoInternoRequest(
    egreso: EgresoRecurso,
    consumo: EgresoConsumoInterno,
    detalles: List[DetalleEgresoRecurso]
  )
  implicit val registrarConsumoInternoFormat: OFormat[RegistrarConsumoInternoRequest] = Json.format[RegistrarConsumoInternoRequest]

  // ===== ENDPOINTS =====

  @cask.options("/api/egresos")
  def egresosOptions() = corsOptions()

  @cask.options("/api/egresos/:id")
  def egresoByIdOptions(id: Int) = corsOptions()

  @cask.options("/api/egresos/ayuda-social")
  def ayudaSocialOptions() = corsOptions()

  @cask.options("/api/egresos/consumo-interno")
  def consumoInternoOptions() = corsOptions()

  @cask.get("/api/egresos")
  def listarEgresos() = {
    try {
      val egresos = egresoRepo.listarEgresos()
      respond(Json.toJson(egresos))
    } catch {
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  @cask.get("/api/egresos/:id")
  def obtenerEgreso(id: Int) = {
    try {
      egresoRepo.obtenerEgreso(id) match {
        case Some(egreso) => respond(Json.toJson(egreso))
        case None => respond(Json.obj("error" -> s"Egreso con ID $id no encontrado"), 404)
      }
    } catch {
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  @cask.post("/api/egresos/ayuda-social")
  def registrarAyudaSocial(request: cask.Request) = {
    try {
      val body = Json.parse(request.text()).as[RegistrarAyudaSocialRequest]
      val egresoNormalizado = normalizarEgreso(body.egreso)
      val id = egresoRepo.registrarAyudaSocial(egresoNormalizado, body.ayuda, body.detalles)
      respond(Json.obj("id" -> id, "mensaje" -> "Ayuda social registrada exitosamente"), 201)
    } catch {
      case e: org.postgresql.util.PSQLException if e.getSQLState == "23503" =>
        respond(Json.obj("error" -> "Referencia inválida: beneficiario o ítem de catálogo no existe"), 409)
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  @cask.post("/api/egresos/consumo-interno")
  def registrarConsumoInterno(request: cask.Request) = {
    try {
      val body = Json.parse(request.text()).as[RegistrarConsumoInternoRequest]
      val egresoNormalizado = normalizarEgreso(body.egreso)
      val id = egresoRepo.registrarConsumoInterno(egresoNormalizado, body.consumo, body.detalles)
      respond(Json.obj("id" -> id, "mensaje" -> "Consumo interno registrado exitosamente"), 201)
    } catch {
      case e: org.postgresql.util.PSQLException if e.getSQLState == "23503" =>
        respond(Json.obj("error" -> "Referencia inválida: responsable o ítem de catálogo no existe"), 409)
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
