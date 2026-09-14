package cl.familiarenacer.sga

import cl.familiarenacer.sga.repositorios.{EntidadRepository, InventarioRepository}
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import io.getquill.{PostgresJdbcContext, SnakeCase}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import java.sql.DriverManager
import java.util.UUID
import scala.util.Using

/** Runs only against an explicitly supplied disposable database; never loads the application's .env. */
class SearchIntegrationSpec extends AnyFunSuite with BeforeAndAfterAll {
  private val url = sys.env.get("SEARCH_TEST_JDBC_URL")
  private val schema = "search_test_" + UUID.randomUUID().toString.replace("-", "")
  private var context: Option[PostgresJdbcContext[SnakeCase.type]] = None
  private def db = { assume(context.nonEmpty, "Define SEARCH_TEST_JDBC_URL con una base desechable"); context.get }
  private def ids(q: String, tipo: Option[String] = None) = new EntidadRepository(db)
    .listarEntidadesUnificadas(tipo, Some(q)).map(_.id).toSet

  override def beforeAll(): Unit = {
    super.beforeAll()
    url.foreach { jdbc =>
      Using.resource(DriverManager.getConnection(jdbc)) { conn =>
        Using.resource(conn.createStatement()) { s =>
          s.execute("CREATE EXTENSION IF NOT EXISTS unaccent")
          s.execute(s"CREATE SCHEMA $schema")
          s.execute(s"SET search_path TO $schema, public")
          s.execute("CREATE TABLE entidad(id INT PRIMARY KEY, rut TEXT, tipo_entidad TEXT, anotaciones TEXT)")
          s.execute("CREATE TABLE persona_natural(entidad_id INT, nombres TEXT, apellidos TEXT, ocupacion TEXT)")
          s.execute("CREATE TABLE institucion(entidad_id INT, razon_social TEXT, nombre_fantasia TEXT)")
          s.execute("INSERT INTO entidad VALUES (1, '12.345.678-K', 'PersonaNatural', NULL), (2, '23456789k', 'PersonaNatural', NULL), (3, NULL, 'PersonaNatural', NULL), (4, NULL, 'Institucion', NULL), (5, NULL, 'PersonaNatural', NULL)")
          s.execute("INSERT INTO persona_natural VALUES (1, 'Gabriel Inti Alejandro', 'Rojas Ferrada', NULL), (2, 'María José', 'Pérez', NULL), (3, 'Gabriela', 'Aros', NULL), (5, 'Valeria', 'Robles', NULL)")
          s.execute("CREATE TABLE item_catalogo(id INT PRIMARY KEY, nombre TEXT, categoria TEXT, unidad_medida_estandar TEXT, stock_actual NUMERIC, valor_total_stock NUMERIC, precio_promedio_ponderado NUMERIC, precio_referencia NUMERIC)")
          s.execute("INSERT INTO item_catalogo VALUES (1, 'Manta térmica', 'Ropa de abrigo', NULL, 0, 0, 0, 0)")
          s.execute("ALTER TABLE persona_natural ADD COLUMN genero TEXT, ADD COLUMN fecha_nacimiento DATE, ADD COLUMN foto_url TEXT")
          s.execute("INSERT INTO institucion VALUES (4, 'Fundación María del Sur', 'Casa Abierta')")
        }
      }
      val config = new HikariConfig()
      config.setJdbcUrl(jdbc)
      config.setConnectionInitSql(s"SET search_path TO $schema, public")
      config.setMaximumPoolSize(2)
      context = Some(new PostgresJdbcContext(SnakeCase, new HikariDataSource(config)))
    }
  }
  override def afterAll(): Unit = try {
    context.foreach(_.close())
    url.foreach(jdbc => Using.resource(DriverManager.getConnection(jdbc))(conn =>
      Using.resource(conn.createStatement())(_.execute(s"DROP SCHEMA IF EXISTS $schema CASCADE"))))
  } finally super.afterAll()

  test("nombre y apellido con nombres intermedios, orden inverso, fragmentos y espacios") {
    for (q <- Seq("Gabriel Rojas", "GABRIEL ROJAS", "rojas gabriel", "  gab   roj  ", "ferrada inti"))
      assert(ids(q) == Set(1), q)
  }
  test("tildes, mayúsculas y unicode descompuesto") {
    for (q <- Seq("MARIA PEREZ", "María Pérez", "perez maria", "mari\u0301a jose\u0301"))
      assert(ids(q) == Set(2), q)
  }
  test("RUT completo y parcial con o sin puntos, guion y K mayúscula") {
    for (q <- Seq("12345678k", "12.345.678-K", "12345678-K", "345678k")) assert(ids(q) == Set(1), q)
    assert(ids("23.456.789-K") == Set(2))
  }
  test("todas las palabras deben coincidir y símbolos SQL no son comodines") {
    for (q <- Seq("Gabriel Perez", "%", "_", "noexiste", "' OR 1=1 --")) assert(ids(q).isEmpty, q)
  }
  test("catálogo admite tildes y palabras repartidas entre nombre y categoría") {
    val repo = new InventarioRepository(db)
    for (q <- Seq("TÉRMICA", "termica ropa", "abrigo manta")) assert(repo.buscarItems(q).map(_.id) == List(1), q)
    assert(repo.buscarItems("%").isEmpty)
    assert(repo.buscarItems("manta inexistente").isEmpty)
  }
  test("PersonaNatural encuentra todas las personas al buscar y al listar") {
    for (tipo <- Seq("PersonaNatural", "personanatural", " PERSONANATURAL ")) {
      for (q <- Seq("Val", "VAL", "Valeria Robles", "robles val"))
        assert(ids(q, Some(tipo)) == Set(5), s"$tipo: $q")
      assert(new EntidadRepository(db).listarEntidadesUnificadas(Some(tipo)).map(_.id).toSet == Set(1, 2, 3, 5))
      assert(new EntidadRepository(db).listarPersonas(Some(tipo)).map(_.entidadId).toSet == Set(1, 2, 3, 5))
    }
    assert(ids("Val", Some("Institucion")).isEmpty)
  }
  test("instituciones, nombre de fantasía y filtro de tipo") {
    assert(ids("sur fundacion") == Set(4))
    assert(ids("abierta casa") == Set(4))
    assert(ids("maria", Some("PersonaNatural")) == Set(2))
    assert(ids("maria", Some("Institucion")) == Set(4))
  }
}
