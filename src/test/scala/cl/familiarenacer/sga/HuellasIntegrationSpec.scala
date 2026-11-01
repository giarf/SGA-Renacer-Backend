package cl.familiarenacer.sga

import cl.familiarenacer.sga.api.routes.HuellasRoutes
import cl.familiarenacer.sga.huellero.{Huellero, HuellaMatcher}
import cl.familiarenacer.sga.modelos._
import cl.familiarenacer.sga.repositorios.HuellaRepository
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import play.api.libs.json._
import java.net.{InetSocketAddress, URI}
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.time.LocalDate
import scala.collection.mutable
import scala.util.Using

/** Huellero falso: entrega las imágenes de la cola en orden y registra lo que se archiva y se borra. */
class HuelleroFalso extends Huellero {
  val cola = mutable.Queue.empty[Either[HuellaError, String]]
  val archivadas = mutable.ArrayBuffer.empty[(Int, Int)]
  val borradas = mutable.ArrayBuffer.empty[Int]
  private def png(nombre: String) = getClass.getResourceAsStream(s"/huellas/$nombre.png").readAllBytes()
  def estado(): JsObject = Json.obj("lector" -> "falso", "listo" -> true)
  def imagen(timeoutSegundos: Option[Int], huellaId: Option[Int], toma: Option[Int]): ImagenHuella = synchronized {
    cola.dequeue() match {
      case Left(e) => throw e
      case Right(nombre) =>
        for (h <- huellaId; n <- toma) archivadas += (h -> n)
        ImagenHuella(png(nombre), 500, 300, 20)
    }
  }
  def tomas(huellaId: Int): Seq[Int] = archivadas.collect { case (`huellaId`, n) => n }.distinct.toSeq
  def toma(huellaId: Int, toma: Int): Array[Byte] = png("matching")
  def borrar(huellaId: Int): Unit = borradas += huellaId
}

class HuellasIntegrationSpec extends AnyFunSuite with BeforeAndAfterAll {
  private var fixture: Option[AsistenciaFixture] = None
  override def beforeAll(): Unit = {
    super.beforeAll()
    fixture = sys.env.get("ASISTENCIA_TEST_JDBC_URL").map(new AsistenciaFixture(_))
    fixture.foreach { f =>
      val sql = new String(getClass.getResourceAsStream("/huellas.sql").readAllBytes(), "UTF-8")
      Using.resource(f.connect())(c => Using.resource(c.createStatement())(_.execute(sql)))
    }
  }
  override def afterAll(): Unit = try fixture.foreach(_.close()) finally super.afterAll()

  test("registrar con 3 tomas, registrar asistencia con un toque, rechazar duplicados y borrar") {
    assume(fixture.nonEmpty, "Define ASISTENCIA_TEST_JDBC_URL con una base de pruebas desechable para ejecutar la integración.")
    val f = fixture.get
    val repo = new HuellaRepository(() => f.connect())
    val huellero = new HuelleroFalso
    val matcher = new HuellaMatcher(umbral = 40, margen = 0, minimoMinucias = 15)
    val eventoId = f.repo.crear(CrearEventoAsistencia("Once", LocalDate.of(2026, 11, 1)))
    val app = new cask.Main { val allRoutes = Seq(new HuellasRoutes(repo, huellero, matcher, f.repo)) }
    val server = io.undertow.Undertow.builder().addHttpListener(0, "127.0.0.1").setHandler(app.defaultHandler).build()
    server.start()
    try {
      val port = server.getListenerInfo.get(0).getAddress.asInstanceOf[InetSocketAddress].getPort
      val client = HttpClient.newHttpClient()
      def post(path: String, body: String) = client.send(HttpRequest.newBuilder(URI.create(s"http://127.0.0.1:$port/api$path"))
        .header("Content-Type", "text/plain").POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString())
      def delete(path: String) = client.send(HttpRequest.newBuilder(URI.create(s"http://127.0.0.1:$port/api$path")).DELETE().build(), HttpResponse.BodyHandlers.ofString())
      def codigo(r: HttpResponse[String]) = (Json.parse(r.body()) \ "codigo").asOpt[String]

      // Sin huellas todavía.
      assert(codigo(post(s"/asistencia/eventos/$eventoId/huella", """{"timeout":10}""")).contains("sin_huellas"))

      // Registro: el segundo toque es de otro dedo y se repite; queda con 3 tomas.
      huellero.cola ++= Seq(Right("probe"), Right("nonmatching"), Right("matching"), Right("probe"))
      val registro = post("/personas/1/huellas", """{"dedo":"indice_derecho"}""")
      assert(registro.statusCode() == 201, registro.body())
      val huellaId = (Json.parse(registro.body()) \ "id").as[Int]
      assert((Json.parse(registro.body()) \ "formato").as[String] == HuellaMatcher.Formato)
      assert(repo.tomas().count(_.huellaId == huellaId) == 3 && matcher.total == 3)
      assert(huellero.archivadas.count(_._1 == huellaId) == 4) // la toma 2 se archivó dos veces

      // Un registro de dedo nuevo que falla no deja fila ni imágenes.
      huellero.cola += Left(HuellaError(408, "Sin dedo", "sin_dedo"))
      assert(post("/personas/2/huellas", """{"dedo":"pulgar_derecho"}""").statusCode() == 408)
      assert(repo.listar(2).isEmpty && huellero.borradas.nonEmpty)

      // La misma huella no puede registrarse a nombre de otra persona.
      huellero.cola += Right("matching")
      val duplicada = post("/personas/2/huellas", """{"dedo":"indice_izquierdo"}""")
      assert(duplicada.statusCode() == 409 && codigo(duplicada).contains("duplicada"), duplicada.body())

      // Un toque: identifica y registra la llegada en la misma llamada; el segundo toque no duplica.
      huellero.cola ++= Seq(Right("matching"), Right("probe"))
      val llegada = post(s"/asistencia/eventos/$eventoId/huella", """{"timeout":10}""")
      assert(llegada.statusCode() == 201, llegada.body())
      val json = Json.parse(llegada.body())
      assert((json \ "persona" \ "id").as[Int] == 1 && (json \ "creada").as[Boolean] && (json \ "puntaje").as[Double] >= 40)
      assert((json \ "tiempos" \ "capturaMs").as[Long] == 300)
      val otra = post(s"/asistencia/eventos/$eventoId/huella", "{}")
      assert(otra.statusCode() == 200 && !(Json.parse(otra.body()) \ "creada").as[Boolean])
      assert(f.repo.detalle(eventoId).evento.totalAsistentes == 1)

      // Otra persona no coincide.
      huellero.cola += Right("nonmatching")
      assert(codigo(post(s"/asistencia/eventos/$eventoId/huella", "{}")).contains("no_coincide"))

      // Borrar quita del caché y de laka.
      assert(delete(s"/personas/1/huellas/$huellaId").statusCode() == 200)
      assert(matcher.vacio && huellero.borradas.contains(huellaId))
    } finally server.stop()
  }
}
