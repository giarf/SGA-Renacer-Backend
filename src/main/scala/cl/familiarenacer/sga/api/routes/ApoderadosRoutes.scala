package cl.familiarenacer.sga.api.routes

import cl.familiarenacer.sga.api.ApiSupport
import cl.familiarenacer.sga.repositorios.{ApoderadoRepository, ApoderadoError}
import play.api.libs.json._

class ApoderadosRoutes(repo: ApoderadoRepository)(implicit cc: castor.Context, log: cask.Logger) extends cask.Routes with ApiSupport {
  case class Vinculo(parentesco: String, esContactoPrincipal: Boolean = false, observaciones: String = "")
  implicit val formato: OFormat[Vinculo] = Json.using[Json.WithDefaultValues].format[Vinculo]
  private def handle(f: => cask.Response[String]): cask.Response[String] = try f catch {
    case e: ApoderadoError => respond(Json.obj("error" -> e.mensaje), e.status)
    case _: JsResultException => respond(Json.obj("error" -> "Datos de vínculo inválidos."), 400)
    case _: com.fasterxml.jackson.core.JsonProcessingException => respond(Json.obj("error" -> "JSON inválido."), 400)
    case e: Exception => e.printStackTrace(); respond(Json.obj("error" -> "No se pudo procesar el vínculo."), 500)
  }
  @cask.options("/api/personas/:id/apoderados")
  def opciones(id: Int) = corsOptions()
  @cask.options("/api/personas/:id/personas-a-cargo")
  def opcionesCargo(id: Int) = corsOptions()
  @cask.options("/api/personas/:id/apoderados/:apoderadoId")
  def opcionesVinculo(id: Int, apoderadoId: Int) = corsOptions()
  @cask.get("/api/personas/:id/apoderados")
  def listar(id: Int) = handle { respond(Json.toJson(repo.listar(id))) }
  @cask.get("/api/personas/:id/personas-a-cargo")
  def cargo(id: Int) = handle { respond(Json.toJson(repo.listar(id, inverso = true))) }
  @cask.put("/api/personas/:id/apoderados/:apoderadoId")
  def guardar(id: Int, apoderadoId: Int, request: cask.Request) = handle {
    val body = Json.parse(request.text()).as[Vinculo]
    repo.guardar(id, apoderadoId, body.parentesco, body.esContactoPrincipal, body.observaciones)
    respond(Json.obj("mensaje" -> "Vínculo guardado."))
  }
  @cask.delete("/api/personas/:id/apoderados/:apoderadoId")
  def quitar(id: Int, apoderadoId: Int) = handle {
    repo.quitar(id, apoderadoId); respond(Json.obj("mensaje" -> "Vínculo eliminado."))
  }
  initialize()
}
