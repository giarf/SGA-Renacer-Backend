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
  def identificar(candidatos: Seq[PlantillaHuella]): Option[Int]
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
      case _: HttpTimeoutException => throw HuellaError(504, "El huellero no recibió una huella a tiempo. Intenta nuevamente.")
      case _: java.io.IOException => throw HuellaError(503, "No se pudo conectar con el huellero. Revisa que el equipo del lector esté encendido.")
    }
    val json = scala.util.Try(Json.parse(response.body())).getOrElse(JsNull)
    if (response.statusCode() >= 400) {
      val mensaje = (json \ "error").asOpt[String].orElse((json \ "mensaje").asOpt[String]).getOrElse("El huellero respondió con un error.")
      // 4xx del huellero (lectura cancelada, dedo mal puesto) se muestran tal cual al operador.
      throw HuellaError(if (response.statusCode() < 500) 422 else 502, mensaje)
    }
    json
  }

  def estado(): JsObject = send(HttpRequest.newBuilder(URI.create(base + "/api/estado")).GET(), Duration.ofSeconds(5))
    .asOpt[JsObject].getOrElse(Json.obj())

  def capturar(): CapturaHuella = post("/api/capturar", Json.obj(), timeoutLectura).asOpt[CapturaHuella]
    .map(HuellaValidacion.captura).getOrElse(throw HuellaError(502, "El huellero devolvió una respuesta inválida."))

  def identificar(candidatos: Seq[PlantillaHuella]): Option[Int] = {
    val body = Json.obj("candidatos" -> candidatos.map(c => Json.obj("id" -> c.id, "plantilla" -> c.plantilla, "formato" -> c.formato)))
    (post("/api/identificar", body, timeoutLectura) \ "id").asOpt[Int]
  }
}
