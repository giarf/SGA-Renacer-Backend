package cl.familiarenacer.sga

import cl.familiarenacer.sga.modelos._
import cl.familiarenacer.sga.repositorios.CampanaRepository
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import java.sql.{Connection, DriverManager}
import java.time.LocalDate
import java.util.UUID
import scala.util.Using
import play.api.libs.json._

class CampanasIntegrationSpec extends AnyFunSuite with BeforeAndAfterAll {
  private val url = sys.env.get("CAMPANAS_TEST_JDBC_URL")
  private val schema = "campanas_test_" + UUID.randomUUID().toString.replace("-", "")
  private def raw(): Connection = DriverManager.getConnection(url.get)
  private def connect(): Connection = {
    val c = raw()
    Using.resource(c.createStatement())(_.execute(s"SET search_path TO $schema"))
    c
  }
  private def repo: CampanaRepository = {
    assume(url.nonEmpty, "Define CAMPANAS_TEST_JDBC_URL con una base desechable para ejecutar integración.")
    new CampanaRepository(() => connect())
  }
  override def beforeAll(): Unit = {
    super.beforeAll()
    if (url.nonEmpty) {
      Using.resource(raw())(c => Using.resource(c.createStatement())(_.execute(s"CREATE SCHEMA $schema")))
      Using.resource(connect()) { c => Using.resource(c.createStatement()) { s =>
        s.execute("CREATE TABLE entidad(id INTEGER PRIMARY KEY, telefono TEXT)")
        s.execute("CREATE TABLE persona_natural(entidad_id INTEGER PRIMARY KEY REFERENCES entidad(id), nombres TEXT NOT NULL, apellidos TEXT)")
        s.execute("INSERT INTO entidad VALUES (1, NULL), (2, NULL), (3, '+56912345678'), (4, '+56987654321')")
        s.execute("INSERT INTO persona_natural VALUES (1, 'Ana', 'Pérez'), (2, 'Pedro', 'Soto'), (3, 'Carolina', NULL), (4, 'José', 'Pérez')")
      }}
      repo.asegurarEsquema()
    }
  }
  override def afterAll(): Unit = try {
    if (url.nonEmpty) Using.resource(raw())(c => Using.resource(c.createStatement())(_.execute(s"DROP SCHEMA $schema CASCADE")))
  } finally super.afterAll()
  private def campaign(): Int = repo.crear(CrearCampana("Navidad", LocalDate.of(2026, 12, 25)))

  test("dos beneficiarios pueden compartir padrino sin duplicar personas ni beneficiarios") {
    val r = repo; r.asegurarEsquema(); val id = campaign()
    val first = r.agregarParticipante(id, RegistrarParticipante(1))
    val second = r.agregarParticipante(id, RegistrarParticipante(2))
    r.asignarPadrino(id, first, AsignarPadrino(Some(3), 1))
    r.asignarPadrino(id, second, AsignarPadrino(Some(3), 1))
    val detail = r.detalle(id)
    assert(detail.campana.totalParticipantes == 2 && detail.campana.sinPadrino == 0)
    assert(detail.participantes.forall(_.padrino.exists(_.id == 3)))
    assert(detail.participantes.head.padrino.get.telefono.contains("+56912345678"))
    assert(intercept[java.sql.SQLException](r.agregarParticipante(id, RegistrarParticipante(1))).getSQLState == "23505")
    assert(intercept[CampanaError](r.asignarPadrino(id, first, AsignarPadrino(Some(1), 2))).status == 400)
  }
  test("cambiar padrino reinicia comunicaciones y rechaza ediciones desactualizadas") {
    val r = repo; val id = campaign(); val row = r.agregarParticipante(id, RegistrarParticipante(1))
    val col = r.detalle(id).columnas.find(_.clave.contains("ficha_enviada")).get.id
    assert(intercept[CampanaError](r.guardarValor(id, row, col, GuardarValorAsistencia(JsBoolean(true), 0))).status == 400)
    r.asignarPadrino(id, row, AsignarPadrino(Some(3), 1))
    val saved = r.guardarValor(id, row, col, GuardarValorAsistencia(JsBoolean(true), 0))
    assert(saved.actualizadoEn.nonEmpty)
    r.asignarPadrino(id, row, AsignarPadrino(Some(4), 2))
    val reset = r.detalle(id).participantes.head.valores(col.toString)
    assert(reset.valor == JsBoolean(false) && reset.version == 2)
    assert(intercept[CampanaError](r.guardarValor(id, row, col, GuardarValorAsistencia(JsBoolean(true), 1))).status == 409)
    assert(intercept[CampanaError](r.asignarPadrino(id, row, AsignarPadrino(None, 2))).status == 409)
  }
  test("campañas aisladas, casillas tipadas y cierre con control de versión") {
    val r = repo; val id = campaign(); val other = campaign()
    val row = r.agregarParticipante(id, RegistrarParticipante(1))
    val foreignCol = r.detalle(other).columnas.head.id
    assert(intercept[CampanaError](r.guardarValor(id, row, foreignCol, GuardarValorAsistencia(JsString("Gustos"), 0))).status == 404)
    val col = r.detalle(id).columnas.find(_.clave.contains("recibido")).get.id
    assert(intercept[AsistenciaError](r.guardarValor(id, row, col, GuardarValorAsistencia(JsString("sí"), 0))).status == 400)
    r.cambiarEstado(id, CambiarEstadoCampana("cerrada", 1))
    assert(intercept[CampanaError](r.guardarValor(id, row, col, GuardarValorAsistencia(JsBoolean(true), 0))).status == 409)
    assert(intercept[CampanaError](r.cambiarEstado(id, CambiarEstadoCampana("activa", 1))).status == 409)
    r.cambiarEstado(id, CambiarEstadoCampana("activa", 2))
    r.guardarValor(id, row, col, GuardarValorAsistencia(JsBoolean(true), 0))
    r.quitarParticipante(id, row)
    assert(r.detalle(id).participantes.isEmpty)
    r.agregarParticipante(id, RegistrarParticipante(1))
    assert(r.detalle(id).participantes.head.valores.isEmpty)
  }
}
