package cl.familiarenacer.sga

import cl.familiarenacer.sga.api.routes.McpRoutes
import cl.familiarenacer.sga.auth.{Autenticacion, FiltroAutenticacion, Requisito}
import cl.familiarenacer.sga.mcp.{ConfigMcp, HerramientasCampanas, ServidorMcp}
import cl.familiarenacer.sga.modelos._
import cl.familiarenacer.sga.repositorios.CampanaRepository
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import play.api.libs.json._

import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.net.{InetSocketAddress, URI}
import java.time.LocalDate

/** Repositorio en memoria: solo listar y detalle, que es lo único que usan las herramientas MCP. */
class CampanaRepositoryFalso extends CampanaRepository(() => throw new IllegalStateException("sin base de datos")) {
  private def contacto(id: Int, nombre: String, gestor: Option[String] = None, ficha: Option[JsObject] = None) =
    ContactoCampana(id, nombre, Some(s"+5690000000$id"), gestor, ficha)
  private val navidad = Campana(1, "Navidad 2026", LocalDate.of(2026, 12, 25), "Regalos para niños", "activa", 1, 3, 1)
  private val invierno = Campana(2, "Invierno", LocalDate.of(2026, 6, 21), "Ropa de abrigo", "cerrada", 3, 1, 0)
  private val columnas = List(
    ColumnaCampana(10, "Regalo recibido", "boolean", Some("recibido"), "", "colaborador", 1),
    ColumnaCampana(11, "Gustos y preferencias", "text", Some("gustos"), "", "colaborador", 1),
    ColumnaCampana(12, "Edad", "ficha", Some("beneficiario.edad"), "", "colaborador", 1),
    ColumnaCampana(13, "Aviso", "whatsapp", None, "Hola", "colaborador", 1))
  private def v(j: JsValue) = ValorCampana(j, 1, "2026-11-23T00:00:00Z")
  private val carolina = contacto(3, "Carolina Muñoz", Some("Gabriel Rojas"))
  private val detalles = Map(
    1 -> DetalleCampana(navidad, columnas, List(
      ParticipanteCampana(100, contacto(1, "Ana Pérez", ficha = Some(Json.obj("edad" -> 7))), Some(carolina), 1,
        Map("10" -> v(JsBoolean(true)), "11" -> v(JsString("Legos")), "13" -> v(Json.obj("texto" -> "Hola", "enviado" -> true)))),
      ParticipanteCampana(101, contacto(2, "Pedro Soto", ficha = Some(Json.obj("edad" -> 9))), Some(carolina), 1, Map("10" -> v(JsBoolean(false)))),
      ParticipanteCampana(102, contacto(5, "José Ramírez"), None, 1, Map.empty))),
    2 -> DetalleCampana(invierno, Nil, List(ParticipanteCampana(200, contacto(3, "Carolina Muñoz"), Some(contacto(1, "Ana Pérez")), 1, Map.empty))))
  override def listar(): List[Campana] = List(navidad, invierno)
  override def detalle(id: Int): DetalleCampana = detalles.getOrElse(id, throw CampanaError(404, "La campaña ya no existe."))
}

class McpSpec extends AnyFunSuite with BeforeAndAfterAll {
  private val servidor = new ServidorMcp(new HerramientasCampanas(new CampanaRepositoryFalso))
  private def rpc(metodo: String, params: JsObject = Json.obj(), id: Int = 1): JsObject =
    servidor.procesar(Json.obj("jsonrpc" -> "2.0", "id" -> id, "method" -> metodo, "params" -> params).toString).cuerpo.get
  private def llamar(nombre: String, args: JsObject): (Boolean, JsValue) = {
    val r = (rpc("tools/call", Json.obj("name" -> nombre, "arguments" -> args)) \ "result").as[JsObject]
    val texto = (r \ "content" \ 0 \ "text").as[String]
    ((r \ "isError").as[Boolean], if ((r \ "isError").as[Boolean]) JsString(texto) else Json.parse(texto))
  }

  test("initialize negocia la versión e incluye instrucciones; la notificación initialized responde 202") {
    val r = rpc("initialize", Json.obj("protocolVersion" -> "2025-06-18", "capabilities" -> Json.obj(),
      "clientInfo" -> Json.obj("name" -> "prueba", "version" -> "1")))
    assert((r \ "id").as[Int] == 1 && (r \ "result" \ "protocolVersion").as[String] == "2025-06-18")
    assert((r \ "result" \ "capabilities" \ "tools").isDefined)
    assert((r \ "result" \ "instructions").as[String].contains("padrino"))
    assert((rpc("initialize", Json.obj("protocolVersion" -> "2099-01-01")) \ "result" \ "protocolVersion").as[String] == ServidorMcp.versiones.head)
    val n = servidor.procesar("""{"jsonrpc":"2.0","method":"notifications/initialized"}""")
    assert(n.status == 202 && n.cuerpo.isEmpty)
    assert((rpc("ping") \ "result").as[JsObject] == Json.obj())
    assert((rpc("no/existe") \ "error" \ "code").as[Int] == -32601)
    assert(servidor.procesar("{no json").status == 400)
  }

  test("tools/list publica las cuatro herramientas de solo lectura con su esquema") {
    val tools = (rpc("tools/list") \ "result" \ "tools").as[List[JsObject]]
    assert(tools.map(t => (t \ "name").as[String]).toSet == Set("listar_campanas", "ver_campana", "resumen_campana", "buscar_en_campanas"))
    tools.foreach { t =>
      assert((t \ "inputSchema" \ "type").as[String] == "object")
      assert((t \ "annotations" \ "readOnlyHint").as[Boolean])
    }
  }

  test("listar_campanas filtra por estado y por texto sin acentos") {
    val (_, todas) = llamar("listar_campanas", Json.obj())
    assert((todas \ "total").as[Int] == 2)
    assert((llamar("listar_campanas", Json.obj("estado" -> "cerrada"))._2 \ "campanas" \ 0 \ "nombre").as[String] == "Invierno")
    assert((llamar("listar_campanas", Json.obj("texto" -> "NIÑOS"))._2 \ "total").as[Int] == 1)
    assert(llamar("listar_campanas", Json.obj("estado" -> "rara"))._1)
  }

  test("ver_campana entrega valores por nombre de columna, filtros y paginación") {
    val (error, d) = llamar("ver_campana", Json.obj("id" -> 1))
    assert(!error && (d \ "totalParticipantes").as[Int] == 3 && (d \ "columnas").as[List[JsObject]].size == 4)
    val ana = (d \ "participantes" \ 0).as[JsObject]
    assert((ana \ "beneficiario" \ "nombre").as[String] == "Ana Pérez")
    assert((ana \ "gestorDelPadrino").as[String] == "Gabriel Rojas")
    assert((ana \ "valores" \ "Regalo recibido").as[Boolean] && (ana \ "valores" \ "Edad").as[Int] == 7)
    assert((ana \ "valores" \ "Aviso").as[String] == "enviado")

    val sin = llamar("ver_campana", Json.obj("id" -> 1, "sin_padrino" -> true))._2
    assert((sin \ "totalFiltrados").as[Int] == 1 && (sin \ "participantes" \ 0 \ "beneficiario" \ "nombre").as[String] == "José Ramírez")
    val busca = llamar("ver_campana", Json.obj("id" -> 1, "buscar" -> "munoz", "columnas" -> Json.arr("gustos y preferencias")))._2
    assert((busca \ "totalFiltrados").as[Int] == 2)
    assert((busca \ "participantes" \ 0 \ "valores").as[JsObject].keys == Set("Gustos y preferencias"))
    val pagina = llamar("ver_campana", Json.obj("id" -> 1, "limite" -> 1, "offset" -> 1))._2
    assert((pagina \ "mostrando").as[Int] == 1 && (pagina \ "hayMas").as[Boolean])

    assert(llamar("ver_campana", Json.obj("id" -> 99)) == (true, JsString("La campaña ya no existe.")))
    assert(llamar("ver_campana", Json.obj("id" -> 1, "columnas" -> Json.arr("Nada")))._1)
    assert(llamar("ver_campana", Json.obj("id" -> 1, "limite" -> 5000))._1)
  }

  test("resumen_campana cuenta padrinos, gestores y celdas por columna") {
    val r = llamar("resumen_campana", Json.obj("id" -> 1))._2
    assert((r \ "participantes" \ "sinPadrino").as[Int] == 1 && (r \ "participantes" \ "padrinosDistintos").as[Int] == 1)
    assert((r \ "padrinosConMasAhijados" \ 0 \ "ahijados").as[Int] == 2)
    assert((r \ "porGestor" \ 0 \ "gestor").as[String] == "Gabriel Rojas")
    val cols = (r \ "columnas").as[List[JsObject]].map(c => (c \ "nombre").as[String] -> c).toMap
    assert((cols("Regalo recibido") \ "marcadas").as[Int] == 1 && (cols("Regalo recibido") \ "vacias").as[Int] == 1)
    assert((cols("Gustos y preferencias") \ "llenas").as[Int] == 1)
    assert((cols("Aviso") \ "enviados").as[Int] == 1 && (cols("Aviso") \ "pendientes").as[Int] == 2)
  }

  test("buscar_en_campanas indica el rol de la persona en cada campaña") {
    val r = llamar("buscar_en_campanas", Json.obj("texto" -> "carolina munoz"))._2
    val apariciones = (r \ "personas" \ 0 \ "apariciones").as[List[JsObject]]
    assert(apariciones.map(a => (a \ "campana").as[String] -> (a \ "rol").as[String]).toSet ==
      Set("Navidad 2026" -> "padrino", "Invierno" -> "beneficiario"))
    assert(llamar("buscar_en_campanas", Json.obj("texto" -> "a"))._1)
  }

  test("tools/call con herramienta desconocida es error JSON-RPC") {
    assert((rpc("tools/call", Json.obj("name" -> "borrar_todo")) \ "error" \ "code").as[Int] == -32602)
  }

  test("reglas de acceso: /mcp exige sesión de operación y los metadatos son públicos") {
    assert(Autenticacion.requisito("POST", "/mcp") == Requisito.Operador)
    assert(Autenticacion.requisito("GET", "/.well-known/oauth-protected-resource") == Requisito.Publico)
    assert(Autenticacion.requisito("GET", "/.well-known/oauth-protected-resource/mcp") == Requisito.Publico)
    assert(ConfigMcp.urlMetadatos("https://api.example.cl/mcp") == "https://api.example.cl/.well-known/oauth-protected-resource/mcp")
  }

  // ===== HTTP con el decorador real =====

  private val app = new cask.Main {
    override def mainDecorators = Seq(new FiltroAutenticacion())
    val allRoutes = Seq(new McpRoutes(new CampanaRepositoryFalso), new cl.familiarenacer.sga.api.routes.CampanasRoutes(new CampanaRepositoryFalso))
  }
  private val server = io.undertow.Undertow.builder().addHttpListener(0, "127.0.0.1").setHandler(app.defaultHandler).build()
  private val client = HttpClient.newHttpClient()
  private var baseUrl = ""
  override def beforeAll(): Unit = {
    super.beforeAll(); server.start()
    baseUrl = s"http://127.0.0.1:${server.getListenerInfo.get(0).getAddress.asInstanceOf[InetSocketAddress].getPort}"
  }
  override def afterAll(): Unit = try { server.stop(); app.executionContext.shutdown() } finally super.afterAll()

  private def send(metodo: String, ruta: String, cuerpo: String = "", headers: Seq[(String, String)] = Nil): HttpResponse[String] = {
    val b = HttpRequest.newBuilder(URI.create(baseUrl + ruta)).method(metodo,
      if (cuerpo.isEmpty) HttpRequest.BodyPublishers.noBody() else HttpRequest.BodyPublishers.ofString(cuerpo))
    headers.foreach { case (k, v) => b.header(k, v) }
    client.send(b.build(), HttpResponse.BodyHandlers.ofString())
  }

  test("el well-known de recurso protegido es público y sigue RFC 9728") {
    Seq("/.well-known/oauth-protected-resource", "/.well-known/oauth-protected-resource/mcp").foreach { ruta =>
      val r = send("GET", ruta)
      assert(r.statusCode() == 200, ruta)
      val j = Json.parse(r.body())
      assert((j \ "resource").as[String].endsWith("/mcp"))
      assert((j \ "authorization_servers").as[List[String]] == List(Autenticacion.issuer))
      assert((j \ "scopes_supported").as[List[String]].contains("offline_access"))
      assert((j \ "bearer_methods_supported").as[List[String]] == List("header"))
    }
  }

  test("POST /mcp sin token o con token inválido da 401 con WWW-Authenticate; GET /mcp da 405") {
    assume(Autenticacion.modo == "enforce", "solo aplica con AUTH_MODE=enforce")
    val cuerpo = """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}"""
    Seq(Nil, Seq("Authorization" -> "Bearer token-falso")).foreach { h =>
      val r = send("POST", "/mcp", cuerpo, h :+ ("Content-Type" -> "application/json"))
      assert(r.statusCode() == 401)
      val www = r.headers().firstValue("WWW-Authenticate").orElse("")
      assert(www.startsWith("Bearer resource_metadata=\"") && www.contains("/.well-known/oauth-protected-resource/mcp\""), www)
    }
    assert(send("GET", "/mcp").statusCode() == 405)
    // Las demás rutas siguen sin el header.
    val api = send("GET", "/api/campanas")
    assert(api.statusCode() == 401 && api.headers().firstValue("WWW-Authenticate").isEmpty)
  }
}
