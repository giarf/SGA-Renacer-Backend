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
        s.execute("""CREATE TABLE entidad(id INTEGER PRIMARY KEY, telefono TEXT, gestor_id INTEGER, rut TEXT, correo TEXT, direccion TEXT,
          comuna TEXT, sector TEXT, region TEXT, red_social TEXT, anotaciones TEXT)""")
        s.execute("""CREATE TABLE persona_natural(entidad_id INTEGER PRIMARY KEY REFERENCES entidad(id), nombres TEXT NOT NULL, apellidos TEXT,
          genero TEXT, ocupacion TEXT, fecha_nacimiento DATE)""")
        s.execute("CREATE TABLE etiqueta(id INTEGER PRIMARY KEY, nombre TEXT NOT NULL)")
        s.execute("CREATE TABLE entidad_etiqueta(entidad_id INTEGER, etiqueta_id INTEGER)")
        s.execute("""CREATE TABLE persona_apoderado(persona_id INTEGER, apoderado_id INTEGER, parentesco TEXT, es_contacto_principal BOOLEAN,
          created_at TIMESTAMPTZ DEFAULT now())""")
        s.execute("INSERT INTO entidad(id, telefono, rut, sector) VALUES (1, NULL, 'PERSONA-1', 'Cerro Mayaca'), (2, NULL, NULL, NULL), (3, '+56912345678', NULL, NULL), (4, '+56987654321', NULL, NULL)")
        s.execute("INSERT INTO persona_natural(entidad_id, nombres, apellidos, fecha_nacimiento) VALUES (1, 'Ana', 'Pérez', current_date - interval '7 years'), (2, 'Pedro', 'Soto', NULL), (3, 'Carolina', NULL, NULL), (4, 'José', 'Pérez', NULL)")
        s.execute("INSERT INTO etiqueta VALUES (6, '#nino')")
        s.execute("INSERT INTO entidad_etiqueta VALUES (1, 6)")
        s.execute("INSERT INTO persona_apoderado(persona_id, apoderado_id, parentesco, es_contacto_principal) VALUES (1, 4, 'Padre', true)")
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
  test("selección única conserva asignaciones al renombrar y rating distingue cero de vacío") {
    val r = repo; val id = campaign()
    val row = r.agregarParticipante(id, RegistrarParticipante(1))
    val recibido = OpcionCampana("recibido", "Recibido")
    val estado = r.agregarColumna(id, CrearColumnaCampana("Estado del regalo", "select", opciones = List(recibido)))
    val rating = r.agregarColumna(id, CrearColumnaCampana("Calificación", "rating"))
    assert(intercept[CampanaError](r.guardarCelda(id, row, estado, GuardarCeldaCampana(JsString("invalida"), 0, Some(1)))).status == 400)
    r.guardarCelda(id, row, estado, GuardarCeldaCampana(JsString(recibido.id), 0, Some(1)))
    r.configurarOpciones(id, estado, ConfigurarOpcionesCampana(List(recibido.copy(nombre = "Ya recibido")), 1))
    val detalle = r.detalle(id)
    assert(detalle.columnas.find(_.id == estado).get.opciones.head.nombre == "Ya recibido")
    assert(detalle.participantes.head.valores(estado.toString).valor == JsString(recibido.id))
    assert(intercept[CampanaError](r.configurarOpciones(id, estado, ConfigurarOpcionesCampana(List(OpcionCampana("otro", "Otro")), 2))).status == 409)
    assert(intercept[CampanaError](r.guardarCelda(id, row, rating, GuardarCeldaCampana(JsNumber(6), 0))).status == 400)
    val cero = r.guardarCelda(id, row, rating, GuardarCeldaCampana(JsNumber(0), 0))
    assert(cero.valor == JsNumber(0))
    assert(r.guardarCelda(id, row, rating, GuardarCeldaCampana(JsNull, cero.version)).valor == JsNull)
  }

  test("columnas de ficha muestran datos de la persona y no se editan desde la campaña") {
    val r = repo; val id = campaign()
    val row = r.agregarParticipante(id, RegistrarParticipante(1))
    r.asignarPadrino(id, row, AsignarPadrino(Some(3), 1))
    val edad = r.agregarColumna(id, CrearColumnaCampana("Edad", "ficha", Some("beneficiario.edad")))
    r.agregarColumna(id, CrearColumnaCampana("Apoderado", "ficha", Some("beneficiario.apoderado")))
    r.agregarColumna(id, CrearColumnaCampana("Teléfono padrino", "ficha", Some("padrino.telefono")))
    val p = r.detalle(id).participantes.head
    val ficha = p.beneficiario.ficha.get
    assert((ficha \ "edad").as[Int] == 7 && (ficha \ "sector").as[String] == "Cerro Mayaca" && (ficha \ "etiquetas").as[String] == "nino")
    assert((ficha \ "rut").toOption.isEmpty)
    assert((ficha \ "apoderado" \ "nombre").as[String] == "José Pérez" && (ficha \ "apoderado" \ "parentesco").as[String] == "Padre")
    assert((p.padrino.get.ficha.get \ "telefono").as[String] == "+56912345678")
    Using.resource(connect())(c => Using.resource(c.createStatement())(_.execute("UPDATE entidad SET gestor_id = 4 WHERE id = 3")))
    val gestor = r.detalle(id).participantes.head.padrino.get.ficha.get
    assert((gestor \ "gestor").as[String] == "José Pérez" && (gestor \ "gestorTelefono").as[String] == "+56987654321")
    assert(intercept[CampanaError](r.agregarColumna(id, CrearColumnaCampana("Edad otra vez", "ficha", Some("beneficiario.edad")))).status == 409)
    assert(intercept[CampanaError](r.agregarColumna(id, CrearColumnaCampana("Clave", "ficha", Some("beneficiario.password")))).status == 400)
    assert(intercept[CampanaError](r.guardarValor(id, row, edad, GuardarValorAsistencia(JsNumber(8), 0))).status == 400)
  }
  test("columnas se reordenan, se eliminan con sus valores y las nuevas quedan al final") {
    val r = repo; val id = campaign()
    val row = r.agregarParticipante(id, RegistrarParticipante(1))
    val originales = r.detalle(id).columnas.map(_.id)
    val nueva = r.agregarColumna(id, CrearColumnaCampana("Talla", "text"))
    assert(r.detalle(id).columnas.map(_.id) == originales :+ nueva)
    r.ordenarColumnas(id, OrdenarColumnasCampana(nueva :: originales))
    assert(r.detalle(id).columnas.map(_.id) == nueva :: originales)
    assert(intercept[CampanaError](r.ordenarColumnas(id, OrdenarColumnasCampana(originales))).status == 409)
    r.guardarValor(id, row, nueva, GuardarValorAsistencia(JsString("M"), 0))
    r.quitarColumna(id, nueva)
    val despues = r.detalle(id)
    assert(despues.columnas.map(_.id) == originales && !despues.participantes.head.valores.contains(nueva.toString))
    assert(intercept[CampanaError](r.quitarColumna(id, nueva)).status == 404)
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
