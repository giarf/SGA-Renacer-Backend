package cl.familiarenacer.sga.modelos

import java.time.LocalDate
import play.api.libs.json._

case class Campana(id: Int, nombre: String, fecha: LocalDate, descripcion: String, estado: String, version: Int, totalParticipantes: Int, sinPadrino: Int)
case class CrearCampana(nombre: String, fecha: LocalDate, descripcion: String = "", plantilla: String = "apadrinamiento")
case class CambiarEstadoCampana(estado: String, version: Int)
case class RegistrarParticipante(beneficiarioId: Int)
case class AsignarPadrino(padrinoId: Option[Int], version: Int)
case class ContactoCampana(id: Int, nombreCompleto: String, telefono: Option[String], gestor: Option[String] = None, ficha: Option[JsObject] = None)
case class OpcionCampana(id: String, nombre: String)
case class ConfigurarOpcionesCampana(opciones: List[OpcionCampana], version: Int)
case class CrearColumnaCampana(nombre: String, tipo: String, campo: Option[String] = None, opciones: List[OpcionCampana] = Nil)

/** Datos de la ficha de una persona que pueden mostrarse como columna de solo lectura en una campaña. */
object CampanaFicha {
  val personas: Set[String] = Set("beneficiario", "padrino")
  val campos: Set[String] = Set("rut", "edad", "fechaNacimiento", "genero", "ocupacion", "telefono", "correo", "direccion",
    "comuna", "sector", "region", "redSocial", "anotaciones", "gestor", "etiquetas", "apoderado")
  def validar(campo: String): String = campo.split('.') match {
    case Array(persona, dato) if personas.contains(persona) && campos.contains(dato) => campo
    case _ => throw CampanaError(400, "Selecciona un dato de la ficha válido.")
  }
}
case class ColumnaCampana(id: Int, nombre: String, tipo: String, clave: Option[String], mensaje: String, destinatario: String, version: Int, opciones: List[OpcionCampana] = Nil)
case class OrdenarColumnasCampana(columnas: List[Int])
case class ConfigurarMensajeCampana(mensaje: String, destinatario: String, version: Int)
case class GuardarCeldaCampana(valor: JsValue, version: Int, columnaVersion: Option[Int] = None)
case class ValorCampana(valor: JsValue, version: Int, actualizadoEn: String)
case class ParticipanteCampana(id: Int, beneficiario: ContactoCampana, padrino: Option[ContactoCampana], version: Int, valores: Map[String, ValorCampana])
case class DetalleCampana(campana: Campana, columnas: List[ColumnaCampana], participantes: List[ParticipanteCampana])
case class CampanaError(status: Int, mensaje: String) extends RuntimeException(mensaje)

object CampanaMensaje {
  def validar(valor: JsValue): Unit = {
    val valido = valor.asOpt[JsObject].exists { obj =>
      obj.keys == Set("texto", "enviado") &&
        (obj \ "texto").asOpt[String].exists(_.length <= 2000) &&
        (obj \ "enviado").asOpt[Boolean].exists(enviado => !enviado || (obj \ "texto").as[String].trim.nonEmpty)
    }
    if (!valido) throw CampanaError(400, "El mensaje debe incluir texto de hasta 2000 caracteres y su marca de enviado.")
  }
}

object CampanasJson {
  implicit val opcionFormat: OFormat[OpcionCampana] = Json.format[OpcionCampana]
  implicit val opcionesFormat: OFormat[ConfigurarOpcionesCampana] = Json.format[ConfigurarOpcionesCampana]
  implicit val configurarFormat: OFormat[ConfigurarMensajeCampana] = Json.format[ConfigurarMensajeCampana]
  implicit val ordenarColumnasFormat: OFormat[OrdenarColumnasCampana] = Json.format[OrdenarColumnasCampana]
  implicit val crearColumnaCampanaFormat: OFormat[CrearColumnaCampana] = Json.using[Json.WithDefaultValues].format[CrearColumnaCampana]
  implicit val celdaFormat: OFormat[GuardarCeldaCampana] = Json.format[GuardarCeldaCampana]
  implicit val campanaFormat: OFormat[Campana] = Json.format[Campana]
  implicit val crearFormat: OFormat[CrearCampana] = Json.using[Json.WithDefaultValues].format[CrearCampana]
  implicit val estadoFormat: OFormat[CambiarEstadoCampana] = Json.format[CambiarEstadoCampana]
  implicit val registrarFormat: OFormat[RegistrarParticipante] = Json.format[RegistrarParticipante]
  implicit val asignarFormat: OFormat[AsignarPadrino] = Json.format[AsignarPadrino]
  implicit val contactoFormat: OFormat[ContactoCampana] = Json.format[ContactoCampana]
  implicit val columnaFormat: OFormat[ColumnaCampana] = Json.format[ColumnaCampana]
  implicit val valorFormat: OFormat[ValorCampana] = Json.format[ValorCampana]
  implicit val participanteFormat: OFormat[ParticipanteCampana] = Json.format[ParticipanteCampana]
  implicit val detalleFormat: OFormat[DetalleCampana] = Json.format[DetalleCampana]
}
