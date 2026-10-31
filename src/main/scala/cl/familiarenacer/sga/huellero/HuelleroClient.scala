package cl.familiarenacer.sga.huellero

import cl.familiarenacer.sga.modelos._
import cl.familiarenacer.sga.modelos.HuellasJson._
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse, HttpTimeoutException}
import java.time.Duration
import play.api.libs.json._

/**
 * Cliente del servicio del PC con el lector DigitalPersona 4500U (huella.familiarenacer.cl).
 * El lector captura y compara; las plantillas se guardan en la base del SGA.
 */
trait Huellero {
  def estado(): JsObject
  def capturar(): CapturaHuella
  def identificar(candidatos: Seq[PlantillaHuella], timeoutSegundos: Option[Int] = None): Option[Int]
}

class HuelleroHttp(
  baseUrl: String = sys.env.getOrElse("HUELLERO_URL", "https://huella.familiarenacer.cl"),
  token: Option[String] = sys.env.get("HUELLERO_TOKEN").filter(_.nonEmpty),
  timeoutLectura: Duration = Duration.ofSeconds(sys.env.get("HUELLERO_TIMEOUT_SEGUNDOS").flatMap(_.toLongOption).getOrElse(60L))
) extends Huellero {
  private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
  private val base = baseUrl.stripSuffix("/")

  private def post(path: String, body: JsValue, timeout: Duration): JsValue = send(
    HttpRequest.newBuilder(URI.create(base + path)).POST(HttpRequest.BodyPublishers.ofString(Json.stringify(body))), timeout)

  private def send(builder: HttpRequest.Builder, timeout: Duration): JsValue = {
    builder.timeout(timeout).header("Content-Type", "application/json").header("Accept", "application/json")
    token.foreach(t => builder.header("Authorization", s"Bearer $t"))
    val response = try client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    catch {
      case _: HttpTimeoutException => throw HuellaError(504, "El huellero no respondió a tiempo. Intenta nuevamente.", "tiempo")
      case _: java.io.IOException => throw HuellaError(503, "No se pudo conectar con el huellero. Revisa que el equipo del lector esté encendido.", "sin_conexion")
    }
    val json = scala.util.Try(Json.parse(response.body())).getOrElse(JsNull)
    if (response.statusCode() >= 400) {
      val mensaje = (json \ "error").asOpt[String].orElse((json \ "mensaje").asOpt[String]).getOrElse("El huellero respondió con un error.")
      // Errores de lectura del huellero se muestran tal cual al operador.
      throw response.statusCode() match {
        case 401 | 403 => HuellaError(502, "El huellero rechazó la clave del SGA. Revisa HUELLERO_TOKEN en el .env del backend y reinícialo.", "clave")
        case 408 => HuellaError(408, mensaje, "sin_dedo")
        case 409 => HuellaError(409, mensaje, "ocupado")
        case 503 => HuellaError(503, mensaje, "sin_conexion")
        case s if s < 500 => HuellaError(422, mensaje)
        case _ => HuellaError(502, mensaje)
      }
    }
    json
  }

  def estado(): JsObject = send(HttpRequest.newBuilder(URI.create(base + "/api/estado")).GET(), Duration.ofSeconds(5))
    .asOpt[JsObject].getOrElse(Json.obj())

  def capturar(): CapturaHuella = post("/api/capturar", Json.obj(), timeoutLectura).asOpt[CapturaHuella]
    .map(HuellaValidacion.captura).getOrElse(throw HuellaError(502, "El huellero devolvió una respuesta inválida."))

  def identificar(candidatos: Seq[PlantillaHuella], timeoutSegundos: Option[Int] = None): Option[Int] = {
    val body = Json.obj("candidatos" -> candidatos.map(c => Json.obj("id" -> c.id, "plantilla" -> c.plantilla, "formato" -> c.formato))) ++
      timeoutSegundos.fold(Json.obj())(t => Json.obj("timeout" -> t))
    val espera = timeoutSegundos.map(t => Duration.ofSeconds(t + 20L)).getOrElse(timeoutLectura)
    (post("/api/identificar", body, espera) \ "id").asOpt[Int]
  }
}
