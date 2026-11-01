package cl.familiarenacer.sga.modelos

import play.api.libs.json._

case class HuellaRegistrada(id: Int, personaId: Int, dedo: String, formato: String, calidad: Option[Int], registrada: String)
/** Una toma guardada: plantilla SourceAFIS serializada (CBOR) de una de las lecturas de un dedo. */
case class TomaGuardada(huellaId: Int, personaId: Int, toma: Int, plantilla: Array[Byte], minucias: Int)
/** Imagen PNG gris que entrega laka, con el tiempo que tardó la captura y el envío. */
case class ImagenHuella(png: Array[Byte], dpi: Double, capturaMs: Long, envioMs: Long)
case class TiemposHuella(capturaMs: Long, envioMs: Long, extraccionMs: Long, comparacionMs: Long, registroMs: Long, totalMs: Long)
case class AsistenciaPorHuella(persona: PersonaIdentificada, asistenciaId: Int, creada: Boolean, puntaje: Double, tiempos: TiemposHuella)
case class RegistrarHuella(dedo: String)
case class PersonaIdentificada(id: Int, nombreCompleto: String, rut: Option[String], dedo: String)

object HuellasJson {
  implicit val huellaFormat: OFormat[HuellaRegistrada] = Json.format[HuellaRegistrada]
  implicit val registrarFormat: OFormat[RegistrarHuella] = Json.format[RegistrarHuella]
  implicit val identificadaFormat: OFormat[PersonaIdentificada] = Json.format[PersonaIdentificada]
  implicit val tiemposFormat: OFormat[TiemposHuella] = Json.format[TiemposHuella]
  implicit val asistenciaHuellaFormat: OFormat[AsistenciaPorHuella] = Json.format[AsistenciaPorHuella]
}

/** codigo permite al frontend distinguir casos sin depender del texto: sin_huellas, no_coincide, sin_dedo, ocupado, sin_conexion, tiempo, clave, calidad_baja. */
case class HuellaError(status: Int, mensaje: String, codigo: String = "") extends RuntimeException(mensaje)

object HuellaValidacion {
  val dedos: Seq[String] = Seq(
    "pulgar_derecho", "indice_derecho", "medio_derecho", "anular_derecho", "menique_derecho",
    "pulgar_izquierdo", "indice_izquierdo", "medio_izquierdo", "anular_izquierdo", "menique_izquierdo"
  )

  def dedo(value: String): String = {
    if (!dedos.contains(value)) throw HuellaError(400, "Selecciona un dedo válido.")
    value
  }

  /** timeout opcional de lectura, entre 2 y 120 segundos. */
  def timeout(body: String): Option[Int] = {
    val t = scala.util.Try(Json.parse(body)).toOption.flatMap(j => (j \ "timeout").asOpt[Int])
    if (t.exists(v => v < 2 || v > 120)) throw HuellaError(400, "timeout debe estar entre 2 y 120 segundos.")
    t
  }
}
