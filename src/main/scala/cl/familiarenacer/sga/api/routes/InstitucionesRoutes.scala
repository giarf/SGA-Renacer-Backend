package cl.familiarenacer.sga.api.routes

import cl.familiarenacer.sga.api.ApiSupport
import cl.familiarenacer.sga.modelos._
import cl.familiarenacer.sga.repositorios.{InstitucionRepository, EntidadRepository}
import play.api.libs.json._

class InstitucionesRoutes(
  institucionRepo: InstitucionRepository,
  entidadRepo: EntidadRepository
)(implicit cc: castor.Context, log: cask.Logger) extends cask.Routes with ApiSupport {

  // ===== DTOs =====

  case class InstitucionCompletaResponse(
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
    razonSocial: String,
    nombreFantasia: Option[String],
    subtipoInstitucion: Option[String],
    rubro: Option[String]
  )
  implicit val institucionCompletaFormat: OFormat[InstitucionCompletaResponse] = Json.format[InstitucionCompletaResponse]

  case class RegistrarInstitucionRequest(
    rut: Option[String],
    telefono: Option[String],
    correo: Option[String],
    direccion: Option[String],
    comuna: Option[String],
    redSocial: Option[String] = None,
    gestorId: Option[Int] = None,
    anotaciones: Option[String] = None,
    sector: Option[String] = None,
    razonSocial: String,
    nombreFantasia: Option[String],
    subtipoInstitucion: Option[String],
    rubro: Option[String]
  )
  implicit val registrarInstitucionFormat: OFormat[RegistrarInstitucionRequest] = Json.format[RegistrarInstitucionRequest]

  case class EditarInstitucionRequest(
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
    razonSocial: String,
    nombreFantasia: Option[String],
    subtipoInstitucion: Option[String],
    rubro: Option[String]
  )
  implicit val editarInstitucionFormat: OFormat[EditarInstitucionRequest] = Json.format[EditarInstitucionRequest]

  // ===== ENDPOINTS =====

  @cask.options("/api/instituciones")
  def institucionesOptions() = corsOptions()

  @cask.options("/api/instituciones/:id")
  def institucionByIdOptions(id: Int) = corsOptions()

  @cask.get("/api/instituciones")
  def listarInstituciones() = {
    try {
      val result = institucionRepo.listarTodasLasInstituciones().map { case (entidad, institucion) =>
        InstitucionCompletaResponse(
          id = entidad.id,
          rut = entidad.rut,
          tipoEntidad = entidad.tipoEntidad,
          telefono = entidad.telefono,
          correo = entidad.correo,
          direccion = entidad.direccion,
          comuna = entidad.comuna,
          redSocial = entidad.redSocial,
          gestorId = entidad.gestorId,
          anotaciones = entidad.anotaciones,
          sector = entidad.sector,
          razonSocial = institucion.razonSocial,
          nombreFantasia = institucion.nombreFantasia,
          subtipoInstitucion = institucion.subtipoInstitucion,
          rubro = institucion.rubro
        )
      }
      respond(Json.toJson(result))
    } catch {
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  @cask.get("/api/instituciones/:id")
  def obtenerInstitucion(id: Int) = {
    try {
      institucionRepo.obtenerInstitucionCompleta(id) match {
        case Some((entidad, institucion)) =>
          val response = InstitucionCompletaResponse(
            id = entidad.id,
            rut = entidad.rut,
            tipoEntidad = entidad.tipoEntidad,
            telefono = entidad.telefono,
            correo = entidad.correo,
            direccion = entidad.direccion,
            comuna = entidad.comuna,
            redSocial = entidad.redSocial,
            gestorId = entidad.gestorId,
            anotaciones = entidad.anotaciones,
            sector = entidad.sector,
            razonSocial = institucion.razonSocial,
            nombreFantasia = institucion.nombreFantasia,
            subtipoInstitucion = institucion.subtipoInstitucion,
            rubro = institucion.rubro
          )
          respond(Json.toJson(response))
        case None =>
          respond(Json.obj("error" -> s"Institución con ID $id no encontrada"), 404)
      }
    } catch {
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  @cask.post("/api/instituciones")
  def registrarInstitucion(request: cask.Request) = {
    try {
      val body = Json.parse(request.text()).as[RegistrarInstitucionRequest]

      val entidad = Entidad(
        id = 0,
        rut = body.rut,
        tipoEntidad = Some("Institucion"),
        telefono = body.telefono,
        correo = body.correo,
        direccion = body.direccion,
        comuna = body.comuna,
        redSocial = body.redSocial,
        gestorId = body.gestorId,
        anotaciones = body.anotaciones,
        sector = body.sector,
        createdAt = Some(java.time.LocalDateTime.now())
      )

      val institucion = Institucion(
        entidadId = 0,
        razonSocial = body.razonSocial,
        nombreFantasia = body.nombreFantasia,
        subtipoInstitucion = body.subtipoInstitucion,
        rubro = body.rubro
      )

      val idGenerado = institucionRepo.registrarInstitucion(institucion, entidad)
      respond(Json.obj("mensaje" -> "Institución registrada exitosamente", "id" -> idGenerado), 201)
    } catch {
      case e: org.postgresql.util.PSQLException if e.getSQLState == "23505" =>
        respond(Json.obj("error" -> "Ya existe una institución registrada con ese RUT"), 409)
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  @cask.put("/api/instituciones/:id")
  def actualizarInstitucion(id: Int, request: cask.Request) = {
    try {
      val body = Json.parse(request.text()).as[EditarInstitucionRequest]
      
      val entidad = Entidad(
        id = id,
        rut = body.rut,
        tipoEntidad = Some("Institucion"),
        telefono = body.telefono,
        correo = body.correo,
        direccion = body.direccion,
        comuna = body.comuna,
        redSocial = body.redSocial,
        gestorId = body.gestorId,
        anotaciones = body.anotaciones,
        sector = body.sector,
        createdAt = None
      )

      val institucion = Institucion(
        entidadId = id,
        razonSocial = body.razonSocial,
        nombreFantasia = body.nombreFantasia,
        subtipoInstitucion = body.subtipoInstitucion,
        rubro = body.rubro
      )

      institucionRepo.actualizarInstitucion(id, institucion, entidad)
      respond(Json.obj("mensaje" -> "Institución actualizada exitosamente"))

    } catch {
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  @cask.delete("/api/instituciones/:id")
  def eliminarInstitucion(id: Int) = {
    try {
      val eliminado = institucionRepo.eliminarInstitucion(id)
      if (eliminado) {
        respond(Json.obj("mensaje" -> "Institución eliminada exitosamente"))
      } else {
        respond(Json.obj("error" -> "No se encontró la institución"), 404)
      }
    } catch {
      case e: org.postgresql.util.PSQLException if e.getSQLState == "23503" =>
        respond(Json.obj("error" -> "No se puede eliminar la institución porque tiene registros asociados"), 409)
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  initialize()
}
