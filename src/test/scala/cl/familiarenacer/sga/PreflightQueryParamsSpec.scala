package cl.familiarenacer.sga

import cl.familiarenacer.sga.api.routes._
import cl.familiarenacer.sga.auth.FiltroAutenticacion
import java.net.{InetSocketAddress, URI}
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite

/**
 * Regresión: el frontend envía "Authorization: Bearer", así que el navegador hace preflight
 * OPTIONS con la misma query string del GET. Los handlers @cask.options no tienen parámetros
 * y cask respondía 400 "Unknown argument: \"q\"", bloqueando todos los buscadores con ?q=.
 * Se monta el mismo decorador global que SgaApiApp para probar el camino real.
 */
class PreflightQueryParamsSpec extends AnyFunSuite with BeforeAndAfterAll {
  private val origin = "https://sga.familiarenacer.cl"
  private val app = new cask.Main {
    override def mainDecorators = Seq(new FiltroAutenticacion())
    val allRoutes = Seq(new EntidadesRoutes(null), new CatalogoRoutes(null), new PersonasRoutes(null, null))
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

  private def send(method: String, path: String, headers: (String, String)*): HttpResponse[String] = {
    val b = HttpRequest.newBuilder(URI.create(baseUrl + path)).method(method, HttpRequest.BodyPublishers.noBody())
    headers.foreach { case (k, v) => b.header(k, v) }
    client.send(b.build(), HttpResponse.BodyHandlers.ofString())
  }

  test("preflight con query string responde 204 con CORS (antes 400 Unknown argument)") {
    for (path <- Seq(
      "/api/entidades", "/api/entidades?q=juan", "/api/entidades?q=juan%20l%C3%B3pez&tipo=PersonaNatural",
      "/api/entidades?q=12.345.678-K", "/api/entidades/existe-rut?rut=1-9", "/api/catalogo/buscar?q=manta"
    )) {
      val r = send("OPTIONS", path, "Origin" -> origin, "Access-Control-Request-Method" -> "GET",
        "Access-Control-Request-Headers" -> "authorization")
      assert(r.statusCode() == 204, s"$path: ${r.statusCode()} ${r.body()}")
      assert(r.headers().firstValue("Access-Control-Allow-Origin").orElse("") == origin, path)
      assert(r.headers().firstValue("Access-Control-Allow-Headers").orElse("").toLowerCase.contains("authorization"), path)
    }
  }

  test("el GET con q sigue exigiendo sesión") {
    assume(cl.familiarenacer.sga.auth.Autenticacion.modo == "enforce", "solo aplica con AUTH_MODE=enforce")
    val r = send("GET", "/api/entidades?q=juan&tipo=PersonaNatural", "Origin" -> origin)
    assert(r.statusCode() == 401)
    assert(r.headers().firstValue("Access-Control-Allow-Origin").orElse("") == origin)
  }
}
