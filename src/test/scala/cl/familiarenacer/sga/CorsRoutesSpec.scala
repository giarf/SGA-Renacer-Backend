package cl.familiarenacer.sga

import cl.familiarenacer.sga.api.routes._
import java.net.{InetSocketAddress, URI}
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite

class CorsRoutesSpec extends AnyFunSuite with BeforeAndAfterAll {
  private val origin = "https://sga.familiarenacer.cl"
  // Preflight must succeed without touching repositories or requiring any database.
  private val app = new cask.Main {
    val allRoutes = Seq(
      new PersonasRoutes(null, null), new EntidadesRoutes(null), new InstitucionesRoutes(null, null),
      new RolesRoutes(null), new FamiliasRoutes(null), new EtiquetasRoutes(null),
      new UbicacionesRoutes(null), new IngresosRoutes(null, null), new EgresosRoutes(null),
      new CatalogoRoutes(null), new SolicitudesRoutes(null), new CuentasRoutes(null), new AsistenciaRoutes(null)
    )
  }
  private val server = io.undertow.Undertow.builder().addHttpListener(0, "127.0.0.1").setHandler(app.defaultHandler).build()
  private val client = HttpClient.newHttpClient()
  private var baseUrl = ""

  override def beforeAll(): Unit = {
    super.beforeAll()
    server.start()
    val port = server.getListenerInfo.get(0).getAddress.asInstanceOf[InetSocketAddress].getPort
    baseUrl = s"http://127.0.0.1:$port"
  }
  override def afterAll(): Unit = try { server.stop(); app.executionContext.shutdown() } finally super.afterAll()

  private def preflight(path: String, method: String, requestOrigin: String = origin): HttpResponse[String] = {
    val request = HttpRequest.newBuilder(URI.create(baseUrl + path))
      .header("Origin", requestOrigin)
      .header("Access-Control-Request-Method", method)
      .header("Access-Control-Request-Headers", "content-type,authorization")
      .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build()
    client.send(request, HttpResponse.BodyHandlers.ofString())
  }

  private def verify(response: HttpResponse[String], method: String): Unit = {
    assert(response.statusCode() == 204, s"${response.uri()}: ${response.statusCode()}")
    assert(response.headers().allValues("Access-Control-Allow-Origin").size() == 1)
    assert(response.headers().firstValue("Access-Control-Allow-Origin").orElse("") == origin)
    assert(response.headers().firstValue("Access-Control-Allow-Credentials").orElse("") == "true")
    assert(response.headers().firstValue("Access-Control-Allow-Methods").orElse("").split(",").map(_.trim).contains(method))
    val headers = response.headers().firstValue("Access-Control-Allow-Headers").orElse("").toLowerCase.split(",").map(_.trim).toSet
    assert(Set("content-type", "authorization").subsetOf(headers))
    assert(response.body().isEmpty)
  }

  test("las rutas que rechazaban preflight aceptan el origen de producción") {
    Seq(
      "/api/entidades" -> "GET", "/api/entidades/existe-rut" -> "GET",
      "/api/familias/1/beneficiarios/1" -> "PUT", "/api/familias/1/beneficiarios/1" -> "DELETE",
      "/api/egresos/1/anular" -> "POST", "/api/personas/test" -> "GET",
      "/api/catalogo/buscar" -> "GET", "/api/catalogo/utilitarios" -> "GET", "/api/catalogo/utilitarios/categorias" -> "GET",
      "/api/beneficiarios" -> "GET", "/api/colaboradores" -> "GET", "/api/trabajadores" -> "GET", "/api/directivos" -> "GET"
    ).foreach { case (path, method) => verify(preflight(path, method), method) }
  }

  test("cada operación publicada tiene un preflight CORS válido") {
    val operations = app.allRoutes.flatMap(_.caskMetadata.value).flatMap { metadata =>
      metadata.endpoint.methods.filterNot(_ == "options").map { method =>
        metadata.endpoint.path.replaceAll(":[^/]+", "1") -> method.toUpperCase
      }
    }.distinct
    assert(operations.nonEmpty)
    operations.foreach { case (path, method) => verify(preflight(path, method), method) }
  }

  test("la respuesta GET real incluye el mismo origen, sin comodines") {
    val request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/personas/test")).header("Origin", origin).GET().build()
    val response = client.send(request, HttpResponse.BodyHandlers.ofString())
    assert(response.statusCode() == 200)
    assert(response.headers().allValues("Access-Control-Allow-Origin").size() == 1)
    assert(response.headers().firstValue("Access-Control-Allow-Origin").orElse("") == origin)
  }

  test("un origen externo no se refleja como origen permitido") {
    val response = preflight("/api/asistencia/eventos", "POST", "https://otro.example")
    verify(response, "POST")
    assert(response.headers().firstValue("Access-Control-Allow-Origin").get() != "https://otro.example")
  }
}
