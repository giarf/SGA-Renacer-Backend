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
//    "Access-Control-Allow-Origin" -> "http://localhost:5173",
    "Access-Control-Allow-Origin" -> "https://sga.familiarenacer.cl",
//    "Access-Control-Allow-Origin" -> "null",
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

  implicit val entidadFormat: OFormat[Entidad] = Json.using[Json.WithDefaultValues].format[Entidad]
  implicit val personaFormat: OFormat[PersonaNatural] = Json.using[Json.WithDefaultValues].format[PersonaNatural]
  implicit val institucionFormat: OFormat[Institucion] = Json.using[Json.WithDefaultValues].format[Institucion]
  implicit val cuentaFinancieraFormat: OFormat[CuentaFinanciera] = Json.using[Json.WithDefaultValues].format[CuentaFinanciera]
  implicit val beneficiarioFormat: OFormat[Beneficiario] = Json.using[Json.WithDefaultValues].format[Beneficiario]
  implicit val colaboradorFormat: OFormat[Colaborador] = Json.using[Json.WithDefaultValues].format[Colaborador]
  implicit val trabajadorFormat: OFormat[Trabajador] = Json.using[Json.WithDefaultValues].format[Trabajador]
  implicit val directivoFormat: OFormat[Directivo] = Json.using[Json.WithDefaultValues].format[Directivo]
  implicit val familiaFormat: OFormat[Familia] = Json.using[Json.WithDefaultValues].format[Familia]
  implicit val ingresoFormat: OFormat[IngresoRecurso] = Json.using[Json.WithDefaultValues].format[IngresoRecurso]
  implicit val donacionFormat: OFormat[IngresoDonacion] = Json.using[Json.WithDefaultValues].format[IngresoDonacion]
  implicit val pecuniarioFormat: OFormat[IngresoPecuniario] = Json.using[Json.WithDefaultValues].format[IngresoPecuniario]
  implicit val compraFormat: OFormat[IngresoCompra] = Json.using[Json.WithDefaultValues].format[IngresoCompra]
  implicit val subvencionFormat: OFormat[IngresoSubvencion] = Json.using[Json.WithDefaultValues].format[IngresoSubvencion]
  implicit val detalleIngresoFormat: OFormat[DetalleIngresoRecurso] = Json.using[Json.WithDefaultValues].format[DetalleIngresoRecurso]
  implicit val egresoFormat: OFormat[EgresoRecurso] = Json.format[EgresoRecurso]
  implicit val ayudaSocialFormat: OFormat[EgresoAyudaSocial] = Json.format[EgresoAyudaSocial]
  implicit val consumoInternoFormat: OFormat[EgresoConsumoInterno] = Json.format[EgresoConsumoInterno]
  implicit val detalleEgresoFormat: OFormat[DetalleEgresoRecurso] = Json.format[DetalleEgresoRecurso]
  implicit val solicitudFormat: OFormat[SolicitudMaterial] = Json.format[SolicitudMaterial]
  implicit val itemSolicitudFormat: OFormat[ItemSolicitud] = Json.format[ItemSolicitud]
  implicit val resumenFormat: OFormat[EntidadResumen] = Json.format[EntidadResumen]
  implicit val ingresoHistorialFormat: OFormat[IngresoHistorial] = Json.format[IngresoHistorial]
  implicit val itemCatalogoFormat: OFormat[ItemCatalogo] = Json.format[ItemCatalogo]
}
