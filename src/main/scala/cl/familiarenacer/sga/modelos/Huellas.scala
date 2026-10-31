package cl.familiarenacer.sga.modelos

import play.api.libs.json._

case class HuellaRegistrada(id: Int, personaId: Int, dedo: String, formato: String, calidad: Option[Int], registrada: String)
case class PlantillaHuella(id: Int, personaId: Int, plantilla: String, formato: String)
case class CapturaHuella(plantilla: String, formato: String = "", calidad: Option[Int] = None)
case class RegistrarHuella(dedo: String)
case class PersonaIdentificada(id: Int, nombreCompleto: String, rut: Option[String], dedo: String)

object HuellasJson {
  implicit val huellaFormat: OFormat[HuellaRegistrada] = Json.format[HuellaRegistrada]
  implicit val capturaFormat: OFormat[CapturaHuella] = Json.using[Json.WithDefaultValues].format[CapturaHuella]
  implicit val registrarFormat: OFormat[RegistrarHuella] = Json.format[RegistrarHuella]
  implicit val identificadaFormat: OFormat[PersonaIdentificada] = Json.format[PersonaIdentificada]
}

case class HuellaError(status: Int, mensaje: String) extends RuntimeException(mensaje)

object HuellaValidacion {
  val dedos: Seq[String] = Seq(
    "pulgar_derecho", "indice_derecho", "medio_derecho", "anular_derecho", "menique_derecho",
    "pulgar_izquierdo", "indice_izquierdo", "medio_izquierdo", "anular_izquierdo", "menique_izquierdo"
  )

  def dedo(value: String): String = {
    if (!dedos.contains(value)) throw HuellaError(400, "Selecciona un dedo válido.")
    value
  }

  def captura(c: CapturaHuella): CapturaHuella = {
    if (c.plantilla.trim.isEmpty || c.plantilla.length > 200000) throw HuellaError(502, "El huellero devolvió una plantilla inválida.")
    if (c.formato.length > 60) throw HuellaError(502, "El huellero devolvió un formato inválido.")
    c.copy(plantilla = c.plantilla.trim, formato = c.formato.trim)
  }
}
