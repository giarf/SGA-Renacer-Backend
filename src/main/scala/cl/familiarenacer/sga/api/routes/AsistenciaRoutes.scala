package cl.familiarenacer.sga.api.routes

import cl.familiarenacer.sga.api.ApiSupport
import cl.familiarenacer.sga.modelos._
import cl.familiarenacer.sga.modelos.AsistenciaJson._
import cl.familiarenacer.sga.repositorios.AsistenciaRepository
import play.api.libs.json._
import scala.util.control.NonFatal

class AsistenciaRoutes(repo: AsistenciaRepository)(implicit cc: castor.Context, log: cask.Logger) extends cask.Routes with ApiSupport {
  private def safely(f: => cask.Response[String]): cask.Response[String] = try f catch {
    case e: AsistenciaError => respond(Json.obj("error" -> e.mensaje), e.status)
    case _: JsResultException => respond(Json.obj("error" -> "Revisa los datos enviados y la fecha del evento."), 400)
    case _: com.fasterxml.jackson.core.JsonProcessingException => respond(Json.obj("error" -> "El contenido enviado no es válido."), 400)
    case e: java.sql.SQLException if e.getSQLState == "23505" => respond(Json.obj("error" -> "Ya existe una columna con ese nombre en el evento."), 409)
    case e: java.sql.SQLException if e.getSQLState == "23503" => respond(Json.obj("error" -> "El evento o la persona ya no existe. Actualiza la lista."), 404)
    case NonFatal(e) =>
      e.printStackTrace()
      respond(Json.obj("error" -> "No se pudo guardar o consultar la asistencia. Intenta nuevamente."), 500)
  }

  @cask.options("/api/asistencia/eventos")
  def eventosOptions() = corsOptions()
  @cask.options("/api/asistencia/eventos/:eventoId")
  def eventoOptions(eventoId: Int) = corsOptions()
  @cask.options("/api/asistencia/eventos/:eventoId/personas")
  def personasOptions(eventoId: Int) = corsOptions()
  @cask.options("/api/asistencia/eventos/:eventoId/personas/:asistenciaId")
  def personaOptions(eventoId: Int, asistenciaId: Int) = corsOptions()
  @cask.options("/api/asistencia/eventos/:eventoId/columnas")
  def columnasOptions(eventoId: Int) = corsOptions()
  @cask.options("/api/asistencia/eventos/:eventoId/columnas/:columnaId")
  def columnaOptions(eventoId: Int, columnaId: Int) = corsOptions()
  @cask.options("/api/asistencia/eventos/:eventoId/personas/:asistenciaId/valores/:columnaId")
  def valorOptions(eventoId: Int, asistenciaId: Int, columnaId: Int) = corsOptions()

  @cask.get("/api/asistencia/eventos")
  def listar() = safely { respond(Json.toJson(repo.listar())) }

  @cask.post("/api/asistencia/eventos")
  def crear(request: cask.Request) = safely {
    respond(Json.obj("id" -> repo.crear(Json.parse(request.text()).as[CrearEventoAsistencia])), 201)
  }

  @cask.get("/api/asistencia/eventos/:eventoId")
  def detalle(eventoId: Int) = safely {
    val response = respond(Json.toJson(repo.detalle(eventoId)))
    response.copy(headers = response.headers ++ Seq("Cache-Control" -> "no-store"))
  }

  @cask.delete("/api/asistencia/eventos/:eventoId")
  def eliminar(eventoId: Int) = safely { repo.eliminar(eventoId); respond(Json.obj("mensaje" -> "Evento eliminado")) }

  @cask.post("/api/asistencia/eventos/:eventoId/personas")
  def agregarPersona(eventoId: Int, request: cask.Request) = safely {
    val body = Json.parse(request.text()).as[RegistrarAsistencia]
    val (id, creada) = repo.agregarPersona(eventoId, body.personaId)
    respond(Json.obj("id" -> id, "creada" -> creada), if (creada) 201 else 200)
  }

  @cask.delete("/api/asistencia/eventos/:eventoId/personas/:asistenciaId")
  def quitarPersona(eventoId: Int, asistenciaId: Int) = safely {
    repo.quitarPersona(eventoId, asistenciaId); respond(Json.obj("mensaje" -> "Asistencia retirada"))
  }

  @cask.post("/api/asistencia/eventos/:eventoId/columnas")
  def agregarColumna(eventoId: Int, request: cask.Request) = safely {
    respond(Json.obj("id" -> repo.agregarColumna(eventoId, Json.parse(request.text()).as[CrearColumnaAsistencia])), 201)
  }

  @cask.delete("/api/asistencia/eventos/:eventoId/columnas/:columnaId")
  def quitarColumna(eventoId: Int, columnaId: Int) = safely {
    repo.quitarColumna(eventoId, columnaId); respond(Json.obj("mensaje" -> "Columna eliminada"))
  }

  @cask.put("/api/asistencia/eventos/:eventoId/personas/:asistenciaId/valores/:columnaId")
  def guardarValor(eventoId: Int, asistenciaId: Int, columnaId: Int, request: cask.Request) = safely {
    respond(Json.toJson(repo.guardarValor(eventoId, asistenciaId, columnaId, Json.parse(request.text()).as[GuardarValorAsistencia])))
  }

  initialize()
}
