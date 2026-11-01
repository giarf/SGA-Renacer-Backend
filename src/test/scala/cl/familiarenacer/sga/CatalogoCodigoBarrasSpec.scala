package cl.familiarenacer.sga

import cl.familiarenacer.sga.api.routes.CatalogoRoutes
import cl.familiarenacer.sga.catalogo.{OpenFoodFacts, ProductoExterno, SugerenciaProducto}
import cl.familiarenacer.sga.modelos._
import cl.familiarenacer.sga.repositorios.InventarioRepository
import java.net.{InetSocketAddress, URI}
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import play.api.libs.json._

class CatalogoCodigoBarrasSpec extends AnyFunSuite with BeforeAndAfterAll {

  test("valida EAN-13 con dígito verificador") {
    assert(CodigoBarras.esValido("7802095190016"))
    assert(CodigoBarras.esValido("7809591400144"))
    assert(!CodigoBarras.esValido("7802095190017"))
  }

  test("acepta EAN-8 y UPC-A, rechaza largos o caracteres inválidos") {
    assert(CodigoBarras.esValido("96385074")) // EAN-8
    assert(CodigoBarras.esValido("036000291452")) // UPC-A
    assert(!CodigoBarras.esValido("036000291453"))
    Seq("", "1234567", "12345678901", "780209519001A", "78020951900160").foreach(c => assert(!CodigoBarras.esValido(c), c))
    assert(CodigoBarras.normalizar(" 7802095-190016 ").contains("7802095190016"))
    assert(CodigoBarras.normalizar("7802095190017").isEmpty)
  }

  private val offEjemplo = Json.parse(
    """{"code":"7802095190016","status":1,"status_verbose":"product found",
      |"product":{"code":"7802095190016","product_name":"Oat tortilla","product_name_es":"Tortilla de avena",
      |"brands":"CAROZZI,Carozzi S.A.","quantity":"425 g","categories":"Panes, Tortillas",
      |"image_front_url":"https://images.openfoodfacts.org/x.jpg"}}""".stripMargin)

  test("arma la sugerencia desde un JSON de Open Food Facts") {
    val s = OpenFoodFacts.sugerencia("7802095190016", offEjemplo).get
    assert(s.nombre == "Tortilla de avena Carozzi 425 g")
    assert(s.marca.contains("Carozzi"))
    assert(s.cantidad.contains("425 g"))
    assert(s.categoria.contains("Panes"))
    assert(s.imagenUrl.contains("https://images.openfoodfacts.org/x.jpg"))
  }

  test("no duplica marca ni cantidad ya presentes en el nombre") {
    assert(OpenFoodFacts.nombreSugerido(Some("Fideos Carozzi spaghetti 5 400g"), Some("Carozzi"), Some("400 g"))
      .contains("Fideos Carozzi spaghetti 5 400g"))
    assert(OpenFoodFacts.nombreSugerido(None, Some("Soprole"), Some("1 L")).contains("Soprole 1 L"))
    assert(OpenFoodFacts.nombreSugerido(None, None, Some("1 L")).isEmpty)
    assert(OpenFoodFacts.capitalizar("MCKAY") == "Mckay" && OpenFoodFacts.capitalizar("McKay") == "McKay")
  }

  test("limpia el símbolo ℮ de la cantidad") {
    val nutella = Json.obj("status" -> 1, "product" -> Json.obj("product_name" -> "Nutella", "brands" -> "Nutella,Ferrero", "quantity" -> "400 g e"))
    assert(OpenFoodFacts.sugerencia("3017620422003", nutella).map(_.nombre).contains("Nutella 400 g"))
    val conSimbolo = Json.obj("status" -> 1, "product" -> Json.obj("product_name" -> "Leche", "quantity" -> "1 L ℮"))
    assert(OpenFoodFacts.sugerencia("1", conSimbolo).flatMap(_.cantidad).contains("1 L"))
  }

  test("ItemCatalogo escribe los campos nuevos como null y conserva los existentes") {
    import cl.familiarenacer.sga.api.ApiSupport
    val json = Json.toJson(ItemCatalogo(1, Some("Arroz"), None, None, None, None, None, Some(BigDecimal(1000))))(new ApiSupport {}.itemCatalogoFormat)
    assert((json \ "codigoBarras").get == JsNull && (json \ "precioReferenciaFecha").get == JsNull && (json \ "origenDatos").get == JsNull)
    assert((json \ "nombre").as[String] == "Arroz" && (json \ "categoria").toOption.isEmpty)
    val leido = Json.obj("id" -> 2, "nombre" -> "Leche").as[ItemCatalogo](new ApiSupport {}.itemCatalogoFormat)
    assert(leido.codigoBarras.isEmpty && leido.nombre.contains("Leche"))
  }

  test("respuesta sin producto o con status 0 no genera sugerencia") {
    assert(OpenFoodFacts.sugerencia("1", Json.obj("status" -> 0, "status_verbose" -> "product not found")).isEmpty)
    assert(OpenFoodFacts.sugerencia("1", Json.obj("status" -> 1, "product" -> Json.obj())).isEmpty)
  }

  test("el request de compras acepta nombres originales y los de donacion-bienes") {
    val rutas = new cl.familiarenacer.sga.api.routes.IngresosRoutes(null, null)(castor.Context.Simple.global, cask.util.Logger.Console.globalLogger)
    import rutas.registrarCompraReads
    val base = Json.obj("ingreso" -> Json.obj("montoTotal" -> 100), "compra" -> Json.obj("cuentaOrigenId" -> 1))
    val original = (base + ("detalles" -> Json.arr(Json.obj("id" -> 0, "itemCatalogoId" -> 5, "cantidad" -> 2, "precioUnitarioIngreso" -> 50))))
      .as[rutas.RegistrarCompraRequest]
    assert(original.detalles.head.itemCatalogoId.contains(5) && original.detalles.head.precioUnitarioIngreso.contains(BigDecimal(50)))
    val estiloDonacion = (base + ("items" -> Json.arr(Json.obj("codigoBarras" -> "7802095190016", "nombre" -> "Tortilla",
      "categoria" -> "Alimento", "unidad" -> "Unidades", "cantidad" -> 2, "precio" -> 50)))).as[rutas.RegistrarCompraRequest]
    val d = estiloDonacion.detalles.head
    assert(d.itemCatalogoId.isEmpty && d.codigoBarras.contains("7802095190016") && d.nombre.contains("Tortilla"))
    assert(d.precioUnitarioIngreso.contains(BigDecimal(50)) && d.unidad.contains("Unidades") && d.cantidad.contains(BigDecimal(2)))
  }

  // ===== Ruta GET /api/catalogo/codigo/:codigo con repositorio y fuente externa falsos =====

  private val itemLocal = ItemCatalogo(7, Some("Leche"), None, Some("Litros"), None, None, None, Some(BigDecimal(990)),
    codigoBarras = Some("7809591400144"), origenDatos = Some("manual"))
  private val repoFalso = new InventarioRepository(null) {
    override def buscarPorCodigo(codigo: String): Option[ItemCatalogo] = Some(itemLocal).filter(_.codigoBarras.contains(codigo))
  }
  private val externoFalso = new ProductoExterno {
    def buscar(codigo: String) = OpenFoodFacts.sugerencia(codigo, offEjemplo).filter(_ => codigo == "7802095190016")
  }
  private val app = new cask.Main { val allRoutes = Seq(new CatalogoRoutes(repoFalso, externoFalso)) }
  private val server = io.undertow.Undertow.builder().addHttpListener(0, "127.0.0.1").setHandler(app.defaultHandler).build()
  private val client = HttpClient.newHttpClient()
  private var baseUrl = ""

  override def beforeAll(): Unit = {
    super.beforeAll()
    server.start()
    baseUrl = s"http://127.0.0.1:${server.getListenerInfo.get(0).getAddress.asInstanceOf[InetSocketAddress].getPort}"
  }
  override def afterAll(): Unit = try { server.stop(); app.executionContext.shutdown() } finally super.afterAll()

  private def get(path: String, method: String = "GET") =
    client.send(HttpRequest.newBuilder(URI.create(baseUrl + path)).method(method, HttpRequest.BodyPublishers.noBody()).build(),
      HttpResponse.BodyHandlers.ofString())

  test("GET /api/catalogo/codigo responde según el contrato") {
    val invalido = get("/api/catalogo/codigo/7802095190017")
    assert(invalido.statusCode() == 400)
    assert((Json.parse(invalido.body()) \ "error").as[String] == "Código de barras inválido")

    val local = Json.parse(get("/api/catalogo/codigo/7809591400144").body())
    assert((local \ "fuente").as[String] == "catalogo")
    assert((local \ "item" \ "id").as[Int] == 7 && (local \ "item" \ "codigoBarras").as[String] == "7809591400144")
    assert((local \ "sugerencia").get == JsNull)

    val off = get("/api/catalogo/codigo/7802095190016")
    assert(off.statusCode() == 200)
    val offJson = Json.parse(off.body())
    assert((offJson \ "fuente").as[String] == "openfoodfacts" && (offJson \ "item").get == JsNull)
    assert((offJson \ "sugerencia" \ "nombre").as[String] == "Tortilla de avena Carozzi 425 g")
    assert((offJson \ "sugerencia" \ "codigoBarras").as[String] == "7802095190016")

    val ninguno = get("/api/catalogo/codigo/96385074")
    assert(ninguno.statusCode() == 404)
    assert((Json.parse(ninguno.body()) \ "codigoBarras").as[String] == "96385074")

    val preflight = get("/api/catalogo/codigo/96385074", "OPTIONS")
    assert(preflight.statusCode() == 204)
    assert(preflight.headers().firstValue("Access-Control-Allow-Origin").orElse("") == "https://sga.familiarenacer.cl")
  }
}
