package cl.familiarenacer.sga

import cl.familiarenacer.sga.api.routes.AsistenciaRoutes
import cl.familiarenacer.sga.modelos._
import cl.familiarenacer.sga.repositorios.AsistenciaRepository
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import play.api.libs.json._
import java.net.{InetSocketAddress, URI}
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.sql.{Connection, DriverManager}
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.{CountDownLatch, Executors, TimeUnit}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.Using

/** Only uses an explicitly configured disposable database, never DATABASE_* or the application's .env. */
class AsistenciaFixture(url: String) {
  private val schema = "asistencia_test_" + UUID.randomUUID().toString.replace("-", "")
  private def raw(): Connection = DriverManager.getConnection(url)
  def connect(): Connection = {
    val conn = raw()
    Using.resource(conn.createStatement())(_.execute(s"SET search_path TO $schema"))
    conn
  }
  Using.resource(raw())(c => Using.resource(c.createStatement())(_.execute(s"CREATE SCHEMA $schema")))
  Using.resource(connect()) { c => Using.resource(c.createStatement()) { s =>
    s.execute("CREATE TABLE entidad(id SERIAL PRIMARY KEY, rut TEXT)")
    s.execute("CREATE TABLE persona_natural(entidad_id INTEGER PRIMARY KEY REFERENCES entidad(id), nombres TEXT NOT NULL, apellidos TEXT)")
    s.execute("INSERT INTO entidad(rut) VALUES ('12.345.678-5'), ('11.111.111-1'), (NULL)")
    s.execute("INSERT INTO persona_natural VALUES (1, 'Ana', 'Pérez'), (2, 'Pedro', 'Soto'), (3, 'María', 'González')")
  }}
  val repo = new AsistenciaRepository(() => connect())
  repo.asegurarEsquema()
  def close(): Unit = Using.resource(raw())(c => Using.resource(c.createStatement())(_.execute(s"DROP SCHEMA $schema CASCADE")))
}

class AsistenciaIntegrationSpec extends AnyFunSuite with BeforeAndAfterAll {
  private var fixture: Option[AsistenciaFixture] = None
  private def repo: AsistenciaRepository = {
    assume(fixture.nonEmpty, "Define ASISTENCIA_TEST_JDBC_URL con una base de pruebas desechable para ejecutar la integración.")
    fixture.get.repo
  }
  override def beforeAll(): Unit = { super.beforeAll(); fixture = sys.env.get("ASISTENCIA_TEST_JDBC_URL").map(new AsistenciaFixture(_)) }
  override def afterAll(): Unit = try fixture.foreach(_.close()) finally super.afterAll()
  private def event(): Int = repo.crear(CrearEventoAsistencia("18 de septiembre", LocalDate.of(2026, 9, 18)))

  private def concurrently[A](actions: Seq[() => A]): Seq[A] = {
    val pool = Executors.newFixedThreadPool(actions.size)
    implicit val ec: ExecutionContext = ExecutionContext.fromExecutor(pool)
    val gate = new CountDownLatch(1)
    try {
      val futures = actions.map(action => Future { assert(gate.await(5, TimeUnit.SECONDS)); action() })
      gate.countDown()
      Await.result(Future.sequence(futures), 20.seconds)
    } finally pool.shutdownNow()
  }

  test("un evento empieza vacío; esquema idempotente y personas sin RUT") {
    val id = event()
    repo.asegurarEsquema()
    assert(repo.detalle(id).asistentes.isEmpty)
    assert(repo.detalle(id).columnas.isEmpty)
    repo.agregarPersona(id, 3)
    val detail = repo.detalle(id)
    assert(detail.evento.totalAsistentes == 1)
    assert(detail.asistentes.head.nombreCompleto == "María González")
    assert(detail.asistentes.head.rut.isEmpty)
  }

  test("dos puestos registrando a la misma persona crean una sola asistencia") {
    val id = event()
    val results = concurrently(Seq.fill(8)(() => repo.agregarPersona(id, 1)))
    assert(results.map(_._1).distinct.size == 1)
    assert(results.count(_._2) == 1)
    assert(repo.detalle(id).evento.totalAsistentes == 1)
  }

  test("las columnas arbitrarias conservan tipos y no aceptan nombres duplicados") {
    val id = event()
    val row = repo.agregarPersona(id, 1)._1
    for ((tipo, value) <- Seq("boolean" -> JsBoolean(true), "text" -> JsString("Vegetariano"), "number" -> JsNumber(2))) {
      val col = repo.agregarColumna(id, CrearColumnaAsistencia(tipo, tipo))
      repo.guardarValor(id, row, col, GuardarValorAsistencia(value, 0))
      assert(repo.detalle(id).asistentes.head.valores(col.toString).valor == value)
    }
    assert(intercept[java.sql.SQLException](repo.agregarColumna(id, CrearColumnaAsistencia(" BOOLEAN ", "text"))).getSQLState == "23505")
    intercept[AsistenciaError](repo.agregarColumna(id, CrearColumnaAsistencia("  ", "text")))
    intercept[AsistenciaError](repo.agregarColumna(id, CrearColumnaAsistencia("Otro", "unknown")))
    val col = repo.detalle(id).columnas.find(_.tipo == "boolean").get.id
    assert(intercept[AsistenciaError](repo.guardarValor(id, row, col, GuardarValorAsistencia(JsString("sí"), 1))).status == 400)
  }

  test("ediciones simultáneas en distintas casillas se conservan") {
    val id = event()
    val row = repo.agregarPersona(id, 1)._1
    val cols = (1 to 6).map(n => repo.agregarColumna(id, CrearColumnaAsistencia(s"Columna $n", "boolean")))
    concurrently(cols.map(col => () => repo.guardarValor(id, row, col, GuardarValorAsistencia(JsBoolean(true), 0))))
    assert(repo.detalle(id).asistentes.head.valores.size == 6)
  }

  test("una edición desactualizada en la misma casilla recibe conflicto sin sobrescribir") {
    val id = event()
    val row = repo.agregarPersona(id, 1)._1
    val col = repo.agregarColumna(id, CrearColumnaAsistencia("Choripán", "boolean"))
    val results = concurrently(Seq(true, false).map(value => () => {
      try { repo.guardarValor(id, row, col, GuardarValorAsistencia(JsBoolean(value), 0)); 200 }
      catch { case e: AsistenciaError => e.status }
    }))
    assert(results.sorted == Seq(200, 409))
    val saved = repo.guardarValor(id, row, col, GuardarValorAsistencia(JsNull, 1))
    assert(saved.version == 2)
    assert(intercept[AsistenciaError](repo.guardarValor(id, row, col, GuardarValorAsistencia(JsBoolean(true), 1))).status == 409)
    assert(repo.detalle(id).asistentes.head.valores(col.toString).valor == JsNull)
  }

  test("no se pueden cruzar columnas ni asistentes entre eventos") {
    val first = event(); val second = event()
    val row = repo.agregarPersona(first, 1)._1
    val col = repo.agregarColumna(second, CrearColumnaAsistencia("Traje típico", "boolean"))
    assert(intercept[AsistenciaError](repo.guardarValor(first, row, col, GuardarValorAsistencia(JsBoolean(true), 0))).status == 404)
    assert(intercept[AsistenciaError](repo.quitarPersona(second, row)).status == 404)
    assert(intercept[java.sql.SQLException](repo.agregarPersona(first, 999999)).getSQLState == "23503")
  }

  test("eliminar asistencias, columnas y eventos limpia respuestas y conserva personas") {
    val id = event()
    val row = repo.agregarPersona(id, 1)._1
    val col = repo.agregarColumna(id, CrearColumnaAsistencia("Nota", "text"))
    repo.guardarValor(id, row, col, GuardarValorAsistencia(JsString("Prueba"), 0))
    repo.quitarColumna(id, col)
    assert(repo.detalle(id).asistentes.head.valores.isEmpty)
    val nextCol = repo.agregarColumna(id, CrearColumnaAsistencia("Nota", "text"))
    repo.guardarValor(id, row, nextCol, GuardarValorAsistencia(JsString("Prueba"), 0))
    repo.quitarPersona(id, row)
    assert(repo.detalle(id).asistentes.isEmpty)
    assert(intercept[AsistenciaError](repo.guardarValor(id, row, nextCol, GuardarValorAsistencia(JsString("Otra"), 1))).status == 404)
    val newRow = repo.agregarPersona(id, 1)._1
    assert(newRow != row)
    assert(repo.detalle(id).asistentes.head.valores.isEmpty)
    repo.eliminar(id)
    assert(intercept[AsistenciaError](repo.detalle(id)).status == 404)
    repo.agregarPersona(event(), 1)
  }

  test("rutas HTTP: validación, CORS, detalle sin caché, escritura y eliminación") {
    val repository = repo
    val app = new cask.Main { val allRoutes = Seq(new AsistenciaRoutes(repository)) }
    val server = io.undertow.Undertow.builder().addHttpListener(0, "127.0.0.1").setHandler(app.defaultHandler).build()
    server.start()
    try {
      val port = server.getListenerInfo.get(0).getAddress.asInstanceOf[InetSocketAddress].getPort
      val client = HttpClient.newHttpClient()
      def request(method: String, path: String = "", body: String = ""): HttpResponse[String] = {
        val req = HttpRequest.newBuilder(URI.create(s"http://127.0.0.1:$port/api/asistencia/eventos$path"))
          .header("Content-Type", "text/plain").method(method, HttpRequest.BodyPublishers.ofString(body)).build()
        client.send(req, HttpResponse.BodyHandlers.ofString())
      }
      assert(request("POST", body = "{").statusCode() == 400)
      assert(request("POST", body = "{}").statusCode() == 400)
      assert(request("POST", body = """{"nombre":"Fiesta","fecha":"2026-02-30"}""").statusCode() == 400)
      val created = request("POST", body = """{"nombre":"Fiesta","fecha":"2026-09-18"}""")
      assert(created.statusCode() == 201)
      val id = (Json.parse(created.body()) \ "id").as[Int]
      val detail = request("GET", s"/$id")
      assert(detail.headers().firstValue("Cache-Control").get() == "no-store")
      assert((Json.parse(detail.body()) \ "asistentes").as[JsArray].value.isEmpty)
      val rowResponse = request("POST", s"/$id/personas", """{"personaId":1}""")
      assert(rowResponse.statusCode() == 201)
      assert(request("POST", s"/$id/personas", """{"personaId":1}""").statusCode() == 200)
      val row = (Json.parse(rowResponse.body()) \ "id").as[Int]
      val colResponse = request("POST", s"/$id/columnas", """{"nombre":"Traje típico","tipo":"boolean"}""")
      assert(colResponse.statusCode() == 201)
      val col = (Json.parse(colResponse.body()) \ "id").as[Int]
      assert(request("PUT", s"/$id/personas/$row/valores/$col", """{"valor":true,"version":0}""").statusCode() == 200)
      assert(request("PUT", s"/$id/personas/$row/valores/$col", """{"valor":false,"version":0}""").statusCode() == 409)
      Seq("", s"/$id", s"/$id/personas", s"/$id/personas/$row", s"/$id/columnas", s"/$id/columnas/$col", s"/$id/personas/$row/valores/$col").foreach { path =>
        val res = request("OPTIONS", path)
        assert(res.statusCode() == 204)
        assert(res.headers().firstValue("Access-Control-Allow-Methods").get().contains("PUT"))
      }
      assert(request("DELETE", s"/$id").statusCode() == 200)
      assert(request("GET", s"/$id").statusCode() == 404)
    } finally { server.stop(); app.executionContext.shutdown() }
  }
}

/** Optional local browser harness with synthetic people and the real attendance API. */
object AsistenciaBrowserFixture {
  def main(args: Array[String]): Unit = {
    val fixture = new AsistenciaFixture(sys.env("ASISTENCIA_TEST_JDBC_URL"))
    val app = new cask.Main {
      override def port = 58089
      override def host = "127.0.0.1"
      val allRoutes = Seq(new AsistenciaRoutes(fixture.repo))
    }
    Runtime.getRuntime.addShutdownHook(new Thread(() => fixture.close()))
    app.main(Array.empty)
    new CountDownLatch(1).await()
  }
}
