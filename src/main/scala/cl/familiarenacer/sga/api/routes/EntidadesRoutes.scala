package cl.familiarenacer.sga.api.routes

import cl.familiarenacer.sga.api.ApiSupport
import cl.familiarenacer.sga.modelos._
import cl.familiarenacer.sga.repositorios.EntidadRepository
import play.api.libs.json._
import java.time.LocalDate

class EntidadesRoutes(entidadRepo: EntidadRepository)(implicit cc: castor.Context, log: cask.Logger) extends cask.Routes with ApiSupport {

  // ===== DTOs =====

  case class ActualizarPersonaRequest(
    id: Int,
    rut: Option[String],
    tipoEntidad: Option[String],
    telefono: Option[String],
    correo: Option[String],
    direccion: Option[String],
    comuna: Option[String],
    redSocial: Option[String] = None,
    gestorId: Option[Int] = None,
    anotaciones: Option[String] = None,
    sector: Option[String] = None,
    nombres: String,
    apellidos: Option[String],
    genero: Option[String],
    ocupacion: Option[String] = None,
    fechaNacimiento: Option[LocalDate] = None
  )
  implicit val actualizarPersonaFormat: OFormat[ActualizarPersonaRequest] = Json.format[ActualizarPersonaRequest]

  // ===== ENDPOINTS =====

  @cask.get("/api/entidades")
  def listarEntidades(tipo: Option[String] = None, q: Option[String] = None) = {
    try {
      val resultado = entidadRepo.listarEntidadesUnificadas(tipo, q)
      respond(Json.toJson(resultado))
    } catch {
      case e: Exception =>
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  @cask.options("/api/entidades/actualizar")
  def actualizarPersonaOptions() = corsOptions()

  @cask.post("/api/entidades/actualizar")
  def actualizarPersonaEndpoint(request: cask.Request) = {
    try {
      val body = Json.parse(request.text()).as[ActualizarPersonaRequest]
      val entidad = Entidad(
        id = body.id, rut = body.rut, tipoEntidad = body.tipoEntidad,
        telefono = body.telefono, correo = body.correo,
        direccion = body.direccion, comuna = body.comuna,
        redSocial = body.redSocial, gestorId = body.gestorId,
        anotaciones = body.anotaciones, sector = body.sector,
        createdAt = None
      )
      val persona = PersonaNatural(
        entidadId = body.id, nombres = body.nombres,
        apellidos = body.apellidos, genero = body.genero,
        ocupacion = body.ocupacion, fechaNacimiento = body.fechaNacimiento
      )
      entidadRepo.actualizarPersonaNatural(body.id, persona, entidad)
      respond(Json.obj("mensaje" -> s"Persona ${body.id} actualizada correctamente"))
    } catch {
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  @cask.get("/api/entidades/existe-rut")
  def existeRut(rut: String) = {
    try {
      val existe = entidadRepo.existeRut(rut)
      respond(Json.obj("rut" -> rut, "existe" -> existe))
    } catch {
      case e: Exception =>
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  initialize()
}
