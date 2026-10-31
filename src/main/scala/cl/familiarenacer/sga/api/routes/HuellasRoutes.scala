package cl.familiarenacer.sga.api.routes

import cl.familiarenacer.sga.api.ApiSupport
import cl.familiarenacer.sga.huellero.Huellero
import cl.familiarenacer.sga.modelos._
import cl.familiarenacer.sga.modelos.HuellasJson._
import cl.familiarenacer.sga.repositorios.HuellaRepository
import play.api.libs.json._
import scala.util.control.NonFatal

class HuellasRoutes(repo: HuellaRepository, huellero: Huellero)(implicit cc: castor.Context, log: cask.Logger) extends cask.Routes with ApiSupport {
  private def safely(f: => cask.Response[String]): cask.Response[String] = try f catch {
    case e: HuellaError => respond(Json.obj("error" -> e.mensaje), e.status)
    case _: JsResultException => respond(Json.obj("error" -> "Revisa los datos enviados."), 400)
    case _: com.fasterxml.jackson.core.JsonProcessingException => respond(Json.obj("error" -> "El contenido enviado no es válido."), 400)
    case e: java.sql.SQLException if e.getSQLState == "23503" => respond(Json.obj("error" -> "La persona ya no existe."), 404)
    case NonFatal(e) =>
      e.printStackTrace()
      respond(Json.obj("error" -> "No se pudo completar la operación con el huellero."), 500)
  }

  @cask.options("/api/huellero/estado")
  def estadoOptions() = corsOptions()
  @cask.options("/api/huellero/identificar")
  def identificarOptions() = corsOptions()
  @cask.options("/api/personas/:id/huellas")
  def huellasOptions(id: Int) = corsOptions()
  @cask.options("/api/personas/:id/huellas/:huellaId")
  def huellaOptions(id: Int, huellaId: Int) = corsOptions()

  @cask.get("/api/huellero/estado")
  def estado() = safely { respond(Json.obj("conectado" -> true) ++ huellero.estado()) }

  /** Pide al lector una huella y la compara contra todas las plantillas guardadas. */
  @cask.post("/api/huellero/identificar")
  def identificar() = safely {
    val candidatos = repo.plantillas()
    if (candidatos.isEmpty) throw HuellaError(404, "Aún no hay huellas registradas.")
    val persona = huellero.identificar(candidatos).flatMap(repo.persona)
      .getOrElse(throw HuellaError(404, "La huella no coincide con ninguna persona registrada."))
    respond(Json.toJson(persona))
  }

  @cask.get("/api/personas/:id/huellas")
  def listar(id: Int) = safely { respond(Json.obj("dedos" -> HuellaValidacion.dedos, "huellas" -> repo.listar(id))) }

  /** Pide al lector capturar una huella y la guarda para el dedo indicado. */
  @cask.post("/api/personas/:id/huellas")
  def registrar(id: Int, request: cask.Request) = safely {
    val dedo = HuellaValidacion.dedo(Json.parse(request.text()).as[RegistrarHuella].dedo)
    repo.listar(id)
    respond(Json.toJson(repo.guardar(id, dedo, huellero.capturar())), 201)
  }

  @cask.delete("/api/personas/:id/huellas/:huellaId")
  def eliminar(id: Int, huellaId: Int) = safely { repo.eliminar(id, huellaId); respond(Json.obj("mensaje" -> "Huella eliminada.")) }

  initialize()
}
