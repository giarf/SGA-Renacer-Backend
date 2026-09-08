package cl.familiarenacer.sga.modelos

import java.time.LocalDate
import play.api.libs.json._

case class EventoAsistencia(id: Int, nombre: String, fecha: LocalDate, descripcion: String, totalAsistentes: Int)
case class ColumnaAsistencia(id: Int, nombre: String, tipo: String)
case class ValorAsistencia(valor: JsValue, version: Int)
case class PersonaAsistente(id: Int, personaId: Int, nombreCompleto: String, rut: Option[String], llegada: String, valores: Map[String, ValorAsistencia])
case class DetalleAsistencia(evento: EventoAsistencia, columnas: List[ColumnaAsistencia], asistentes: List[PersonaAsistente])
case class CrearEventoAsistencia(nombre: String, fecha: LocalDate, descripcion: String = "")
case class CrearColumnaAsistencia(nombre: String, tipo: String)
case class RegistrarAsistencia(personaId: Int)
case class GuardarValorAsistencia(valor: JsValue, version: Int)

object AsistenciaJson {
  implicit val eventoFormat: OFormat[EventoAsistencia] = Json.format[EventoAsistencia]
  implicit val columnaFormat: OFormat[ColumnaAsistencia] = Json.format[ColumnaAsistencia]
  implicit val valorFormat: OFormat[ValorAsistencia] = Json.format[ValorAsistencia]
  implicit val asistenteFormat: OFormat[PersonaAsistente] = Json.format[PersonaAsistente]
  implicit val detalleFormat: OFormat[DetalleAsistencia] = Json.format[DetalleAsistencia]
  implicit val crearEventoFormat: OFormat[CrearEventoAsistencia] = Json.using[Json.WithDefaultValues].format[CrearEventoAsistencia]
  implicit val crearColumnaFormat: OFormat[CrearColumnaAsistencia] = Json.format[CrearColumnaAsistencia]
  implicit val registrarFormat: OFormat[RegistrarAsistencia] = Json.format[RegistrarAsistencia]
  implicit val guardarFormat: OFormat[GuardarValorAsistencia] = Json.format[GuardarValorAsistencia]
}

case class AsistenciaError(status: Int, mensaje: String) extends RuntimeException(mensaje)

object AsistenciaValidacion {
  def nombre(value: String): String = {
    val result = value.trim
    if (result.isEmpty || result.length > 120) throw AsistenciaError(400, "El nombre debe tener entre 1 y 120 caracteres.")
    result
  }

  def tipo(value: String): String = {
    if (!Set("boolean", "text", "number").contains(value)) throw AsistenciaError(400, "Selecciona un tipo de columna válido.")
    value
  }

  def valor(tipo: String, value: JsValue): Unit = {
    val valid = value == JsNull || (tipo match {
      case "boolean" => value.isInstanceOf[JsBoolean]
      case "text" => value.asOpt[String].exists(_.length <= 2000)
      case "number" => value.asOpt[BigDecimal].exists(n => n.abs <= BigDecimal("1000000000000"))
      case _ => false
    })
    if (!valid) throw AsistenciaError(400, "El valor no corresponde al tipo de columna o supera el límite permitido.")
  }
}
