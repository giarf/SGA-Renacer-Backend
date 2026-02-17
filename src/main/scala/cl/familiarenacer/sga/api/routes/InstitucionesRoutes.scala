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
  // Note: Endpoint handlers for instituciones CRUD are defined as DTOs for now.
  // Full handlers will be implemented when the frontend consumes them.

  initialize()
}
