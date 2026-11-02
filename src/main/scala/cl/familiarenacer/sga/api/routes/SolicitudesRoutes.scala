package cl.familiarenacer.sga.api.routes

import cl.familiarenacer.sga.api.ApiSupport
import cl.familiarenacer.sga.modelos._
import cl.familiarenacer.sga.repositorios.{SolicitudDetalle, SolicitudRepository}
import play.api.libs.json._

import java.time.LocalDate

class SolicitudesRoutes(solicitudRepo: SolicitudRepository)(implicit cc: castor.Context, log: cask.Logger) extends cask.Routes with ApiSupport {

  // ===== DTOs =====

  case class SolicitudRequest(solicitud: SolicitudMaterial, items: List[ItemSolicitud])
  implicit val solicitudRequestFormat: OFormat[SolicitudRequest] = Json.format[SolicitudRequest]

  case class ItemEntregaRequest(itemCatalogoId: Int, cantidad: BigDecimal)
  implicit val itemEntregaFormat: OFormat[ItemEntregaRequest] = Json.format[ItemEntregaRequest]

  case class EntregarSolicitudRequest(
    fecha: Option[LocalDate] = None,
    responsableInternoId: Int,
    anotaciones: Option[String] = None,
    items: List[ItemEntregaRequest]
  )
  implicit val entregarSolicitudFormat: OFormat[EntregarSolicitudRequest] =
    Json.using[Json.WithDefaultValues].format[EntregarSolicitudRequest]

  private def solicitudJson(d: SolicitudDetalle): JsObject =
    Json.toJsObject(d.solicitud) ++ Json.obj("items" -> d.items, "egresoIds" -> d.egresoIds)

  private def manejar(accion: => cask.Response[String]): cask.Response[String] =
    try accion
    catch {
      case e: NoSuchElementException => respond(Json.obj("error" -> e.getMessage), 404)
      case e: IllegalArgumentException => respond(Json.obj("error" -> e.getMessage), 400)
      case e: JsResultException => respond(Json.obj("error" -> s"Datos inválidos: ${e.getMessage}"), 400)
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }

  // ===== ENDPOINTS =====

  @cask.options("/api/solicitudes")
  def solicitudesOptions() = corsOptions()

  @cask.options("/api/solicitudes/:id")
  def solicitudByIdOptions(id: Int) = corsOptions()

  @cask.options("/api/solicitudes/:id/entregar")
  def entregarOptions(id: Int) = corsOptions()

  @cask.options("/api/solicitudes/:id/cancelar")
  def cancelarOptions(id: Int) = corsOptions()

  @cask.get("/api/solicitudes")
  def listarSolicitudes() = manejar {
    respond(JsArray(solicitudRepo.listarSolicitudes().map(solicitudJson)))
  }

  @cask.get("/api/solicitudes/:id")
  def obtenerSolicitud(id: Int) = manejar {
    solicitudRepo.obtenerSolicitud(id) match {
      case Some(detalle) => respond(solicitudJson(detalle))
      case None => respond(Json.obj("error" -> s"Solicitud $id no encontrada"), 404)
    }
  }

  @cask.post("/api/solicitudes")
  def crearSolicitud(request: cask.Request) = manejar {
    val body = Json.parse(request.text()).as[SolicitudRequest]
    val id = solicitudRepo.crearSolicitud(body.solicitud, body.items)
    respond(Json.obj("id" -> id, "mensaje" -> "Solicitud registrada", "items_count" -> body.items.size), 201)
  }

  /** Entrega de una vez: genera el consumo interno con lo que salió de verdad y cierra la solicitud. */
  @cask.post("/api/solicitudes/:id/entregar")
  def entregarSolicitud(id: Int, request: cask.Request) = manejar {
    val body = Json.parse(request.text()).as[EntregarSolicitudRequest]
    val egresoId = solicitudRepo.entregarSolicitud(
      id, body.fecha, body.responsableInternoId, body.anotaciones, body.items.map(i => i.itemCatalogoId -> i.cantidad)
    )
    respond(Json.obj("egresoId" -> egresoId, "mensaje" -> "Solicitud entregada"), 201)
  }

  @cask.post("/api/solicitudes/:id/cancelar")
  def cancelarSolicitud(id: Int) = manejar {
    solicitudRepo.cancelarSolicitud(id)
    respond(Json.obj("mensaje" -> "Solicitud cancelada"))
  }

  initialize()
}
