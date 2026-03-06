package cl.familiarenacer.sga.api

import cl.familiarenacer.sga.modelos._
import play.api.libs.json._
import play.api.libs.functional.syntax._

/**
 * Trait compartido por todas las rutas del API.
 * Provee headers CORS, helper de respuesta JSON, y formatters implícitos.
 */
trait ApiSupport {

  // CORS Headers
  val corsHeaders: Seq[(String, String)] = Seq(
    "Access-Control-Allow-Origin" -> "https://sga.familiarenacer.cl",
    "Access-Control-Allow-Credentials" -> "true",
    "Access-Control-Allow-Methods" -> "GET, POST, PUT, PATCH, DELETE, OPTIONS",
    "Access-Control-Allow-Headers" -> "Content-Type, Authorization, X-Requested-With, Accept, Origin",
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
  implicit val egresoFormat: OFormat[EgresoRecurso] = OFormat(
    (
      (__ \ "id").readWithDefault[Int](0) and
      (__ \ "fecha").readNullable[java.time.LocalDate] and
      (__ \ "tipoEgreso").readNullable[String] and
      (
        (__ \ "montoTotal").readNullable[BigDecimal] orElse
          (__ \ "montoValorizadoTotal").readNullable[BigDecimal]
      ) and
      (__ \ "responsableInternoId").readNullable[Int] and
      (__ \ "anotaciones").readNullable[String] and
      (__ \ "destinoEntidadId").readNullable[Int] and
      (__ \ "propositoEspecifico").readNullable[String]
    )(EgresoRecurso.apply _),
    Json.writes[EgresoRecurso]
  )
  implicit val egresoPecuniarioFormat: OFormat[EgresoPecuniario] = Json.using[Json.WithDefaultValues].format[EgresoPecuniario]
  implicit val detalleEgresoFormat: OFormat[DetalleEgresoRecurso] = Json.using[Json.WithDefaultValues].format[DetalleEgresoRecurso]
  implicit val egresoDetalleFormat: OFormat[EgresoDetalle] = Json.format[EgresoDetalle]
  implicit val solicitudFormat: OFormat[SolicitudMaterial] = Json.format[SolicitudMaterial]
  implicit val itemSolicitudFormat: OFormat[ItemSolicitud] = Json.format[ItemSolicitud]
  implicit val resumenFormat: OFormat[EntidadResumen] = Json.format[EntidadResumen]
  implicit val ingresoHistorialWrites: OWrites[IngresoHistorial] = OWrites { ingreso =>
    val base = Json.obj(
      "id" -> ingreso.id,
      "tipo" -> ingreso.tipo
    )

    val fechaJson = ingreso.fecha.map(f => Json.obj("fecha" -> Json.toJson(f))).getOrElse(Json.obj())
    val montoJson = ingreso.montoTotal.map(m => Json.obj("montoTotal" -> Json.toJson(m))).getOrElse(Json.obj())
    val estadoJson = ingreso.estado.map(e => Json.obj("estado" -> Json.toJson(e))).getOrElse(Json.obj())
    val descJson = ingreso.descripcion.map(d => Json.obj("descripcion" -> Json.toJson(d))).getOrElse(Json.obj())

    base ++ fechaJson ++ montoJson ++ estadoJson ++ descJson
  }
  implicit val itemCatalogoFormat: OFormat[ItemCatalogo] = Json.format[ItemCatalogo]
}
