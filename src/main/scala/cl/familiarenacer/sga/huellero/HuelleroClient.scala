package cl.familiarenacer.sga.huellero

import cl.familiarenacer.sga.modelos._
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse, HttpTimeoutException}
import java.time.Duration
import play.api.libs.json._

/**
 * Cliente del servicio del PC con el lector DigitalPersona 4500U (huella.familiarenacer.cl).
 * Laka solo captura y archiva imágenes; las plantillas y la comparación viven en el SGA.
 */
trait Huellero {
  def estado(): JsObject
  /** Captura una imagen. Con huellaId y toma, laka además la archiva para poder regenerar plantillas. */
  def imagen(timeoutSegundos: Option[Int], huellaId: Option[Int] = None, toma: Option[Int] = None): ImagenHuella
  def tomas(huellaId: Int): Seq[Int]
  def toma(huellaId: Int, toma: Int): Array[Byte]
  def borrar(huellaId: Int): Unit
}

class HuelleroHttp(
  baseUrl: String = sys.env.getOrElse("HUELLERO_URL", "https://huella.familiarenacer.cl"),
  token: Option[String] = sys.env.get("HUELLERO_TOKEN").filter(_.nonEmpty),
  tokenAdmin: Option[String] = sys.env.get("HUELLERO_TOKEN_ADMIN").filter(_.nonEmpty),
  timeoutLectura: Int = sys.env.get("HUELLERO_TIMEOUT_SEGUNDOS").flatMap(_.toIntOption).getOrElse(40)
) extends Huellero {
  // HTTP/2 y conexión reutilizada: evita un TLS nuevo por cada toque.
  private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
  private val base = baseUrl.stripSuffix("/")

  private def request(path: String, clave: Option[String], espera: Duration): HttpRequest.Builder = {
    val b = HttpRequest.newBuilder(URI.create(base + path)).timeout(espera)
    clave.foreach(t => b.header("Authorization", s"Bearer $t"))
    b
  }

  private def send(builder: HttpRequest.Builder): HttpResponse[Array[Byte]] = {
    val response = try client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray())
    catch {
      case _: HttpTimeoutException => throw HuellaError(504, "El huellero no respondió a tiempo. Intenta nuevamente.", "tiempo")
      case _: java.io.IOException => throw HuellaError(503, "No se pudo conectar con el huellero. Revisa que el equipo del lector esté encendido.", "sin_conexion")
    }
    if (response.statusCode() >= 400) {
      val json = scala.util.Try(Json.parse(response.body())).getOrElse(JsNull)
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
    response
  }

  private def json(response: HttpResponse[Array[Byte]]): JsValue =
    scala.util.Try(Json.parse(response.body())).getOrElse(throw HuellaError(502, "El huellero devolvió una respuesta inválida."))

  def estado(): JsObject = json(send(request("/api/estado", token, Duration.ofSeconds(5)).GET()))
    .asOpt[JsObject].getOrElse(Json.obj())

  def imagen(timeoutSegundos: Option[Int], huellaId: Option[Int], toma: Option[Int]): ImagenHuella = {
    val segundos = timeoutSegundos.getOrElse(timeoutLectura)
    val body = Json.obj("timeout" -> segundos) ++
      huellaId.fold(Json.obj())(id => Json.obj("huella_id" -> id)) ++ toma.fold(Json.obj())(n => Json.obj("toma" -> n))
    val inicio = System.nanoTime()
    val response = send(request("/api/imagen", token, Duration.ofSeconds(segundos + 15L))
      .header("Content-Type", "application/json").header("Accept", "image/png")
      .POST(HttpRequest.BodyPublishers.ofString(Json.stringify(body))))
    val totalMs = (System.nanoTime() - inicio) / 1000000
    val png = response.body()
    if (png.length < 8 || png(1) != 'P' || png(2) != 'N' || png(3) != 'G') throw HuellaError(502, "El huellero no devolvió una imagen válida.")
    val header = (n: String) => response.headers().firstValue(n).orElse("")
    val dpi = header("X-Huella-Dpi").toDoubleOption.filter(d => d >= 200 && d <= 1500).getOrElse(500.0)
    val capturaMs = header("X-Huella-Captura-Ms").toDoubleOption.map(_.toLong).getOrElse(totalMs)
    ImagenHuella(png, dpi, capturaMs, math.max(0L, totalMs - capturaMs))
  }

  def tomas(huellaId: Int): Seq[Int] =
    (json(send(request(s"/api/huellas/$huellaId/tomas", tokenAdmin, Duration.ofSeconds(15)).GET())) \ "tomas").asOpt[Seq[Int]].getOrElse(Nil)

  def toma(huellaId: Int, toma: Int): Array[Byte] =
    send(request(s"/api/huellas/$huellaId/tomas/$toma", tokenAdmin, Duration.ofSeconds(15)).GET()).body()

  def borrar(huellaId: Int): Unit = send(request(s"/api/huellas/$huellaId", token, Duration.ofSeconds(10)).DELETE())
}
