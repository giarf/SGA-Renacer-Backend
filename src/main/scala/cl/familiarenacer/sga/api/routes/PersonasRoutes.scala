package cl.familiarenacer.sga.api.routes

import cl.familiarenacer.sga.api.ApiSupport
import cl.familiarenacer.sga.modelos._
import cl.familiarenacer.sga.repositorios.EntidadRepository
import play.api.libs.json._
import java.time.LocalDate

class PersonasRoutes(entidadRepo: EntidadRepository)(implicit cc: castor.Context, log: cask.Logger) extends cask.Routes with ApiSupport {

  // ===== DTOs =====

  case class EditarPersonaRequest(
    id: Int,
    rut: Option[String],
    tipoEntidad: String,
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
  implicit val editarPersonaFormat: OFormat[EditarPersonaRequest] = Json.format[EditarPersonaRequest]

  case class PersonaCompletaResponse(
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
  implicit val personaCompletaFormat: OFormat[PersonaCompletaResponse] = Json.format[PersonaCompletaResponse]

  case class RegistrarPersonaRequest(
    rut: Option[String],
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
  implicit val registrarPersonaFormat: OFormat[RegistrarPersonaRequest] = Json.format[RegistrarPersonaRequest]

  // ===== ENDPOINTS =====

  @cask.get("/api/personas/test")
  def test() = {
    cask.Response(data = "Test", statusCode = 200, headers = corsHeaders)
  }

  @cask.options("/api/personas")
  def personasOptions() = corsOptions()

  @cask.options("/api/personas/:id")
  def personaByIdOptions(id: Int) = corsOptions()

  @cask.get("/api/personas")
  def listarPersonas() = {
    try {
      val personasDB = entidadRepo.listarTodasLasPersonas()
      val resultado = personasDB.map { case (entidad, persona) =>
        PersonaCompletaResponse(
          id = entidad.id, rut = entidad.rut, tipoEntidad = entidad.tipoEntidad,
          telefono = entidad.telefono, correo = entidad.correo,
          direccion = entidad.direccion, comuna = entidad.comuna,
          redSocial = entidad.redSocial, gestorId = entidad.gestorId,
          anotaciones = entidad.anotaciones, sector = entidad.sector,
          nombres = persona.nombres, apellidos = persona.apellidos,
          genero = persona.genero, ocupacion = persona.ocupacion,
          fechaNacimiento = persona.fechaNacimiento
        )
      }
      respond(Json.toJson(resultado))
    } catch {
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  @cask.get("/api/personas/:id")
  def obtenerPersona(id: Int) = {
    try {
      entidadRepo.obtenerPersonaCompleta(id) match {
        case Some((entidad, persona)) =>
          val response = PersonaCompletaResponse(
            id = entidad.id, rut = entidad.rut, tipoEntidad = entidad.tipoEntidad,
            telefono = entidad.telefono, correo = entidad.correo,
            direccion = entidad.direccion, comuna = entidad.comuna,
            redSocial = entidad.redSocial, gestorId = entidad.gestorId,
            anotaciones = entidad.anotaciones, sector = entidad.sector,
            nombres = persona.nombres, apellidos = persona.apellidos,
            genero = persona.genero, ocupacion = persona.ocupacion,
            fechaNacimiento = persona.fechaNacimiento
          )
          respond(Json.toJson(response))
        case None =>
          respond(Json.obj("error" -> s"ID $id no encontrado"), 404)
      }
    } catch {
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  @cask.post("/api/personas")
  def registrarPersona(request: cask.Request): cask.Response[String] = {
    val maybeBody: Either[cask.Response[String], RegistrarPersonaRequest] = try {
      Right(Json.parse(request.text()).as[RegistrarPersonaRequest])
    } catch {
      case e: Exception =>
        Left(respond(Json.obj("error" -> "JSON inválido o campos faltantes"), 400))
    }

    maybeBody match {
      case Left(errorResponse) => errorResponse
      case Right(body) =>
        try {
          val nuevaEntidad = Entidad(
            id = 0, rut = body.rut, tipoEntidad = Some("Persona"),
            telefono = body.telefono, correo = body.correo,
            direccion = body.direccion, comuna = body.comuna,
            redSocial = body.redSocial, gestorId = body.gestorId,
            anotaciones = body.anotaciones, sector = body.sector,
            createdAt = Some(java.time.LocalDateTime.now())
          )
          val nuevaPersona = PersonaNatural(
            entidadId = 0, nombres = body.nombres,
            apellidos = body.apellidos, genero = body.genero,
            ocupacion = body.ocupacion, fechaNacimiento = body.fechaNacimiento
          )
          val idGenerado = entidadRepo.registrarPersonaNatural(nuevaPersona, nuevaEntidad)
          respond(Json.obj("mensaje" -> "Persona creada exitosamente", "id" -> idGenerado), 201)
        } catch {
          case e: org.postgresql.util.PSQLException if e.getSQLState == "23505" =>
            respond(Json.obj("error" -> s"Ya existe una entidad registrada con el RUT ${body.rut.getOrElse("")}"), 409)
          case e: Exception =>
            e.printStackTrace()
            respond(Json.obj("error" -> e.getMessage), 500)
        }
    }
  }

  @cask.put("/api/personas/:id")
  def editarPersona(id: Int, request: cask.Request) = {
    try {
      val body = Json.parse(request.text()).as[EditarPersonaRequest]
      val entidad = Entidad(
        id = id, rut = body.rut, tipoEntidad = Some(body.tipoEntidad),
        telefono = body.telefono, correo = body.correo,
        direccion = body.direccion, comuna = body.comuna,
        redSocial = body.redSocial, gestorId = body.gestorId,
        anotaciones = body.anotaciones, sector = body.sector,
        createdAt = None
      )
      val persona = PersonaNatural(
        entidadId = id, nombres = body.nombres,
        apellidos = body.apellidos, genero = body.genero,
        ocupacion = body.ocupacion, fechaNacimiento = body.fechaNacimiento
      )
      val rowsUpdated = entidadRepo.actualizarPersonaNatural(id, persona, entidad)
      if (rowsUpdated > 0) {
        respond(Json.obj("mensaje" -> "Persona actualizada exitosamente", "filasActualizadas" -> rowsUpdated))
      } else {
        respond(Json.obj("error" -> s"No se encontró la persona con ID $id"), 404)
      }
    } catch {
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  @cask.delete("/api/personas/:id")
  def eliminarPersona(id: Int) = {
    try {
      val eliminado = entidadRepo.eliminarPersona(id)
      if (eliminado) {
        respond(Json.obj("mensaje" -> "Persona eliminada exitosamente"))
      } else {
        respond(Json.obj("error" -> "No se encontró la persona con ese ID"), 404)
      }
    } catch {
      case e: org.postgresql.util.PSQLException if e.getSQLState == "23503" =>
        respond(Json.obj("error" -> "No se puede eliminar la persona porque tiene registros asociados"), 409)
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  initialize()
}
