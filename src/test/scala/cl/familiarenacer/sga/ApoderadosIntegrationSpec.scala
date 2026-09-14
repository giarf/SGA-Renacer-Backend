package cl.familiarenacer.sga

import cl.familiarenacer.sga.repositorios.{ApoderadoRepository, ApoderadoError}
import java.sql.DriverManager
import java.util.UUID
import org.scalatest.funsuite.AnyFunSuite
import scala.util.Using

class ApoderadosIntegrationSpec extends AnyFunSuite {
  test("vínculos bidireccionales, principal único, edición, validación y eliminación sin borrar personas") {
    val url = sys.env.get("APODERADOS_TEST_JDBC_URL")
    assume(url.nonEmpty, "Define APODERADOS_TEST_JDBC_URL con una base de pruebas desechable")
    val schema = "apoderados_" + UUID.randomUUID().toString.replace("-", "")
    def connect() = {
      val c = DriverManager.getConnection(url.get)
      Using.resource(c.createStatement())(_.execute(s"SET search_path TO $schema, public"))
      c
    }
    Using.resource(DriverManager.getConnection(url.get)) { c =>
      Using.resource(c.createStatement()) { s =>
        s.execute(s"CREATE SCHEMA $schema")
        s.execute(s"SET search_path TO $schema, public")
        s.execute("CREATE TABLE entidad(id INT PRIMARY KEY, telefono TEXT, rut TEXT)")
        s.execute("CREATE TABLE persona_natural(entidad_id INT PRIMARY KEY REFERENCES entidad(id), nombres TEXT, apellidos TEXT)")
        s.execute("INSERT INTO entidad VALUES(1,NULL,NULL),(2,'123',NULL),(3,'456',NULL)")
        s.execute("INSERT INTO persona_natural VALUES(1,'Niña','Prueba'),(2,'Madre','Prueba'),(3,'Padre','Prueba')")
      }
    }
    try {
      val repo = new ApoderadoRepository(() => connect())
      val ddl = Using.resource(scala.io.Source.fromInputStream(getClass.getResourceAsStream("/apoderados.sql")))(_.mkString)
      Using.resource(connect()) { c => Using.resource(c.createStatement()) { s => s.execute(ddl); s.execute(ddl) } }
      repo.guardar(1, 2, "Madre", true, "contacto")
      assert((repo.listar(2, true).head \ "personaId").as[Int] == 1)
      repo.guardar(1, 3, "Padre", true, "")
      assert(repo.listar(1).count(r => (r \ "esContactoPrincipal").as[Boolean]) == 1)
      assert((repo.listar(1).head \ "apoderadoId").as[Int] == 3)
      repo.guardar(1, 2, "Madre", true, "editado")
      assert(repo.listar(1).size == 2)
      assert((repo.listar(1).head \ "observaciones").as[String] == "editado")
      assert(intercept[ApoderadoError](repo.guardar(1, 1, "Otro", false, "")).status == 400)
      assert(intercept[ApoderadoError](repo.guardar(1, 99, "Otro", true, "")).status == 404)
      assert((repo.listar(1).head \ "apoderadoId").as[Int] == 2)
      repo.quitar(1, 2); repo.quitar(1, 3)
      assert(repo.listar(1).isEmpty)
      assert(repo.listar(2, true).isEmpty)
    } finally Using.resource(DriverManager.getConnection(url.get))(c => Using.resource(c.createStatement())(_.execute(s"DROP SCHEMA $schema CASCADE")))
  }
}
