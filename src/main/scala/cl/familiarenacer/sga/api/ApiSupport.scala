package cl.familiarenacer.sga.api

import cl.familiarenacer.sga.modelos._
import play.api.libs.json._

/**
 * Trait compartido por todas las rutas del API.
 * Provee headers CORS, helper de respuesta JSON, y formatters implícitos.
 */
trait ApiSupport {

  // CORS Headers
  val corsHeaders = Seq(
    "Access-Control-Allow-Origin" -> "http://localhost:5173",
    "Access-Control-Allow-Origin" -> "null",
    "Access-Control-Allow-Methods" -> "GET, POST, PUT, DELETE, OPTIONS",
    "Access-Control-Allow-Headers" -> "Content-Type, Authorization",
    "Access-Control-Max-Age" -> "86400"
  )

  /**
   * Genera una respuesta JSON estandarizada con headers de CORS.
   */
  def respond(data: JsValue, statusCode: Int = 200): cask.Response[String] = {
    cask.Response(
      data = data.toString(),
      statusCode = statusCode,
      headers = Seq("Content-Type" -> "application/json") ++ corsHeaders
    )
  }

  def corsOptions(): cask.Response[String] = {
    cask.Response(data = "", statusCode = 204, headers = corsHeaders)
  }

  // ===== JSON Formatters Implícitos =====

  implicit val entidadFormat: OFormat[Entidad] = Json.format[Entidad]
  implicit val personaFormat: OFormat[PersonaNatural] = Json.format[PersonaNatural]
  implicit val institucionFormat: OFormat[Institucion] = Json.format[Institucion]
  implicit val cuentaFinancieraFormat: OFormat[CuentaFinanciera] = Json.format[CuentaFinanciera]
  implicit val beneficiarioFormat: OFormat[Beneficiario] = Json.format[Beneficiario]
  implicit val colaboradorFormat: OFormat[Colaborador] = Json.format[Colaborador]
  implicit val trabajadorFormat: OFormat[Trabajador] = Json.format[Trabajador]
  implicit val directivoFormat: OFormat[Directivo] = Json.format[Directivo]
  implicit val familiaFormat: OFormat[Familia] = Json.format[Familia]
  implicit val ingresoFormat: OFormat[IngresoRecurso] = Json.format[IngresoRecurso]
  implicit val donacionFormat: OFormat[IngresoDonacion] = Json.format[IngresoDonacion]
  implicit val pecuniarioFormat: OFormat[IngresoPecuniario] = Json.format[IngresoPecuniario]
  implicit val compraFormat: OFormat[IngresoCompra] = Json.format[IngresoCompra]
  implicit val subvencionFormat: OFormat[IngresoSubvencion] = Json.format[IngresoSubvencion]
  implicit val detalleIngresoFormat: OFormat[DetalleIngresoRecurso] = Json.format[DetalleIngresoRecurso]
  implicit val egresoFormat: OFormat[EgresoRecurso] = Json.format[EgresoRecurso]
  implicit val ayudaSocialFormat: OFormat[EgresoAyudaSocial] = Json.format[EgresoAyudaSocial]
  implicit val consumoInternoFormat: OFormat[EgresoConsumoInterno] = Json.format[EgresoConsumoInterno]
  implicit val detalleEgresoFormat: OFormat[DetalleEgresoRecurso] = Json.format[DetalleEgresoRecurso]
  implicit val solicitudFormat: OFormat[SolicitudMaterial] = Json.format[SolicitudMaterial]
  implicit val itemSolicitudFormat: OFormat[ItemSolicitud] = Json.format[ItemSolicitud]
  implicit val resumenFormat: OFormat[EntidadResumen] = Json.format[EntidadResumen]
  implicit val itemCatalogoFormat: OFormat[ItemCatalogo] = Json.format[ItemCatalogo]
}
