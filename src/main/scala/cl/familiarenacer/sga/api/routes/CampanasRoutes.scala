package cl.familiarenacer.sga.api.routes

import cl.familiarenacer.sga.api.ApiSupport
import cl.familiarenacer.sga.modelos._
import cl.familiarenacer.sga.modelos.CampanasJson._
import cl.familiarenacer.sga.modelos.AsistenciaJson.{crearColumnaFormat, guardarFormat}
import cl.familiarenacer.sga.repositorios.CampanaRepository
import play.api.libs.json._
import scala.util.control.NonFatal

class CampanasRoutes(repo: CampanaRepository)(implicit cc: castor.Context, log: cask.Logger) extends cask.Routes with ApiSupport {
  private def safely(f: => cask.Response[String]): cask.Response[String] = try f catch {
    case e: CampanaError => respond(Json.obj("error" -> e.mensaje), e.status)
    case e: AsistenciaError => respond(Json.obj("error" -> e.mensaje), e.status)
    case _: JsResultException => respond(Json.obj("error" -> "Revisa los datos y la fecha de la campaña."), 400)
    case _: com.fasterxml.jackson.core.JsonProcessingException => respond(Json.obj("error" -> "El contenido enviado no es válido."), 400)
    case e: java.sql.SQLException if e.getSQLState == "23505" => respond(Json.obj("error" -> "El beneficiario o el nombre de columna ya está registrado en esta campaña."), 409)
    case e: java.sql.SQLException if e.getSQLState == "23503" => respond(Json.obj("error" -> "La campaña o la persona ya no existe."), 404)
    case NonFatal(e) => e.printStackTrace(); respond(Json.obj("error" -> "No se pudo guardar o consultar la campaña. Intenta nuevamente."), 500)
  }
  @cask.options("/api/campanas")
  def opciones() = corsOptions()
  @cask.options("/api/campanas/:id")
  def detalleOptions(id: Int) = corsOptions()
  @cask.options("/api/campanas/:id/estado")
  def estadoOptions(id: Int) = corsOptions()
  @cask.options("/api/campanas/:id/participantes")
  def participantesOptions(id: Int) = corsOptions()
  @cask.options("/api/campanas/:id/participantes/:participanteId")
  def participanteOptions(id: Int, participanteId: Int) = corsOptions()
  @cask.options("/api/campanas/:id/participantes/:participanteId/padrino")
  def padrinoOptions(id: Int, participanteId: Int) = corsOptions()
  @cask.options("/api/campanas/:id/columnas")
  def columnasOptions(id: Int) = corsOptions()
  @cask.options("/api/campanas/:id/participantes/:participanteId/valores/:columnaId")
  def valorOptions(id: Int, participanteId: Int, columnaId: Int) = corsOptions()

  @cask.get("/api/campanas")
  def listar() = safely { respond(Json.toJson(repo.listar())) }
  @cask.post("/api/campanas")
  def crear(request: cask.Request) = safely { respond(Json.obj("id" -> repo.crear(Json.parse(request.text()).as[CrearCampana])), 201) }
  @cask.get("/api/campanas/:id")
  def detalle(id: Int) = safely {
    val response = respond(Json.toJson(repo.detalle(id)))
    response.copy(headers = response.headers ++ Seq("Cache-Control" -> "no-store"))
  }
  @cask.put("/api/campanas/:id/estado")
  def estado(id: Int, request: cask.Request) = safely {
    repo.cambiarEstado(id, Json.parse(request.text()).as[CambiarEstadoCampana]); respond(Json.obj("mensaje" -> "Estado actualizado"))
  }
  @cask.post("/api/campanas/:id/participantes")
  def agregar(id: Int, request: cask.Request) = safely { respond(Json.obj("id" -> repo.agregarParticipante(id, Json.parse(request.text()).as[RegistrarParticipante])), 201) }
  @cask.put("/api/campanas/:id/participantes/:participanteId/padrino")
  def asignar(id: Int, participanteId: Int, request: cask.Request) = safely {
    repo.asignarPadrino(id, participanteId, Json.parse(request.text()).as[AsignarPadrino]); respond(Json.obj("mensaje" -> "Padrino actualizado"))
  }
  @cask.delete("/api/campanas/:id/participantes/:participanteId")
  def quitar(id: Int, participanteId: Int) = safely { repo.quitarParticipante(id, participanteId); respond(Json.obj("mensaje" -> "Participante retirado")) }
  @cask.post("/api/campanas/:id/columnas")
  def columna(id: Int, request: cask.Request) = safely { respond(Json.obj("id" -> repo.agregarColumna(id, Json.parse(request.text()).as[CrearColumnaAsistencia])), 201) }
  @cask.put("/api/campanas/:id/participantes/:participanteId/valores/:columnaId")
  def valor(id: Int, participanteId: Int, columnaId: Int, request: cask.Request) = safely {
    respond(Json.toJson(repo.guardarValor(id, participanteId, columnaId, Json.parse(request.text()).as[GuardarValorAsistencia])))
  }
  initialize()
}
