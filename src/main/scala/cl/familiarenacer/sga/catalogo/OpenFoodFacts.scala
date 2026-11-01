package cl.familiarenacer.sga.catalogo

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.text.Normalizer
import java.time.Duration
import java.util.Locale
import play.api.libs.json._

/** Datos sugeridos para crear un ítem del catálogo a partir de un código de barras externo. */
case class SugerenciaProducto(
  codigoBarras: String,
  nombre: String,
  marca: Option[String],
  cantidad: Option[String],
  categoria: Option[String],
  imagenUrl: Option[String]
)

object SugerenciaProducto {
  implicit val writes: OWrites[SugerenciaProducto] = OWrites { s =>
    Json.obj(
      "codigoBarras" -> s.codigoBarras,
      "nombre" -> s.nombre,
      "marca" -> s.marca,
      "cantidad" -> s.cantidad,
      "categoria" -> s.categoria,
      "imagenUrl" -> s.imagenUrl
    )
  }
}

/** Fuente externa de productos por código de barras. None si no existe, falla o tarda demasiado. */
trait ProductoExterno {
  def buscar(codigo: String): Option[SugerenciaProducto]
}

class OpenFoodFactsHttp(
  baseUrl: String = "https://world.openfoodfacts.org",
  timeout: Duration = Duration.ofSeconds(4)
) extends ProductoExterno {
  private val client = HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NORMAL).build()

  def buscar(codigo: String): Option[SugerenciaProducto] = {
    val url = s"${baseUrl.stripSuffix("/")}/api/v2/product/$codigo.json?fields=${OpenFoodFacts.campos}"
    val request = HttpRequest.newBuilder(URI.create(url))
      .timeout(timeout)
      .header("User-Agent", OpenFoodFacts.userAgent)
      .header("Accept", "application/json")
      .GET().build()
    try {
      val response = client.send(request, HttpResponse.BodyHandlers.ofString())
      if (response.statusCode() != 200) None
      else OpenFoodFacts.sugerencia(codigo, Json.parse(response.body()))
    } catch {
      case e: Exception =>
        println(s"Open Food Facts no respondió para $codigo: ${e.getClass.getSimpleName} ${e.getMessage}")
        None
    }
  }
}

/** Funciones puras para interpretar respuestas de Open Food Facts. */
object OpenFoodFacts {
  val userAgent = "SGA-Renacer/1.0 (contacto@familiarenacer.cl)"
  val campos = "code,product_name,product_name_es,brands,quantity,categories,image_front_url"

  private def texto(json: JsValue, campo: String): Option[String] =
    (json \ campo).asOpt[String].map(_.trim.replaceAll("\\s+", " ")).filter(_.nonEmpty)

  def sugerencia(codigo: String, respuesta: JsValue): Option[SugerenciaProducto] = {
    val encontrado = (respuesta \ "status").asOpt[Int].forall(_ == 1)
    val producto = (respuesta \ "product").toOption.filter(_ => encontrado)
    producto.flatMap { p =>
      val base = texto(p, "product_name_es").orElse(texto(p, "product_name"))
      val marca = texto(p, "brands").flatMap(_.split(",").map(_.trim).find(_.nonEmpty)).map(capitalizar)
      // "℮" (cantidad estimada UE) llega a veces como "e" suelto al final: "400 g e".
      val cantidad = texto(p, "quantity").map(_.replaceAll("\\s*(℮|\\be)$", "").trim).filter(_.nonEmpty)
      val categoria = texto(p, "categories").flatMap(
        _.split(",").map(_.trim.replaceFirst("^[a-z]{2}:", "")).find(_.nonEmpty)
      )
      nombreSugerido(base, marca, cantidad).map { nombre =>
        SugerenciaProducto(codigo, nombre, marca, cantidad, categoria, texto(p, "image_front_url"))
      }
    }
  }

  /** Nombre + marca + cantidad, sin repetir la marca o la cantidad si el nombre ya las incluye. */
  def nombreSugerido(nombre: Option[String], marca: Option[String], cantidad: Option[String]): Option[String] = {
    val partes = Seq.newBuilder[String]
    val base = nombre.map(capitalizarInicial)
    base.foreach(partes += _)
    marca.filterNot(m => base.exists(b => contiene(b, m))).foreach(partes += _)
    cantidad.filterNot(c => base.exists(b => contiene(b, c))).foreach(partes += _)
    val resultado = partes.result()
    // Sin nombre ni marca no hay nada útil que sugerir.
    if (base.isEmpty && marca.isEmpty) None else Some(resultado.mkString(" "))
  }

  private def comparable(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
      .toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "")

  private def contiene(texto: String, parte: String): Boolean = {
    val p = comparable(parte)
    p.nonEmpty && comparable(texto).contains(p)
  }

  /** "CAROZZI" o "carozzi" -> "Carozzi"; respeta marcas con mayúsculas internas como "McKay". */
  def capitalizar(marca: String): String =
    marca.split(" ").filter(_.nonEmpty).map { palabra =>
      val esUniforme = palabra == palabra.toUpperCase(Locale.ROOT) || palabra == palabra.toLowerCase(Locale.ROOT)
      if (esUniforme) palabra.toLowerCase(Locale.ROOT).capitalize else palabra
    }.mkString(" ")

  private def capitalizarInicial(s: String): String =
    if (s == s.toUpperCase(Locale.ROOT)) s.toLowerCase(Locale.ROOT).capitalize else s.capitalize
}
