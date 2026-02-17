package cl.familiarenacer.sga.api.routes

import cl.familiarenacer.sga.api.ApiSupport
import cl.familiarenacer.sga.modelos._
import cl.familiarenacer.sga.repositorios.SolicitudRepository
import play.api.libs.json._

class SolicitudesRoutes(solicitudRepo: SolicitudRepository)(implicit cc: castor.Context, log: cask.Logger) extends cask.Routes with ApiSupport {

  // ===== DTOs =====

  case class SolicitudRequest(solicitud: SolicitudMaterial, items: List[ItemSolicitud])
  implicit val solicitudRequestFormat: OFormat[SolicitudRequest] = Json.format[SolicitudRequest]

  case class ActualizarSolicitudRequest(
    estado: String,
    autorizadorId: Option[Int]
  )
  implicit val actualizarSolicitudFormat: OFormat[ActualizarSolicitudRequest] = Json.format[ActualizarSolicitudRequest]

  // ===== ENDPOINTS =====

  @cask.options("/api/solicitudes")
  def solicitudesOptions() = corsOptions()

  @cask.post("/api/solicitudes")
  def crearSolicitud(request: cask.Request) = {
    try {
      val body = Json.parse(request.text()).as[SolicitudRequest]
      respond(Json.obj("mensaje" -> "Solicitud recibida", "items_count" -> body.items.size), 201)
    } catch {
      case e: Exception =>
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  initialize()
}
