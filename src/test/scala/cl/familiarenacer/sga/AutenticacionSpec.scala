package cl.familiarenacer.sga

import cl.familiarenacer.sga.auth.{Autenticacion, Requisito, UsuarioSesion}
import cl.familiarenacer.sga.usuarios.{AuthentikAdmin, AuthentikError, AuthentikHttp}
import com.nimbusds.jose.crypto.{MACSigner, RSASSASigner}
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.jwk.{JWKSet, RSAKey}
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jose.{JWSAlgorithm, JWSHeader}
import com.nimbusds.jwt.{JWTClaimsSet, SignedJWT}
import org.scalatest.funsuite.AnyFunSuite
import play.api.libs.json._

import java.util.Date
import scala.collection.mutable
import scala.jdk.CollectionConverters._

class AutenticacionSpec extends AnyFunSuite {

  private val emisor = "https://auth.example.test/application/o/sga-renacer/"
  private val cliente = "sga-renacer-frontend"
  private val clave: RSAKey = new RSAKeyGenerator(2048).keyID("prueba").generate()
  private val procesador = Autenticacion.crearProcesador(new ImmutableJWKSet[SecurityContext](new JWKSet(clave.toPublicJWK)), emisor, cliente)

  private def token(
    iss: String = emisor,
    aud: String = cliente,
    venceEnSeg: Long = 3600,
    grupos: Seq[String] = Seq("renacer-miembros"),
    firmante: RSAKey = clave
  ): String = {
    val ahora = System.currentTimeMillis()
    val claims = new JWTClaimsSet.Builder()
      .issuer(iss).audience(aud).subject("abc123")
      .issueTime(new Date(ahora)).expirationTime(new Date(ahora + venceEnSeg * 1000))
      .claim("preferred_username", "maria").claim("name", "María Pérez").claim("email", "maria@renacer.cl")
      .claim("groups", grupos.asJava)
      .build()
    val jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(firmante.getKeyID).build(), claims)
    jwt.sign(new RSASSASigner(firmante))
    jwt.serialize()
  }

  private def validar(t: String) = Autenticacion.validar(Some(s"Bearer $t"), procesador)

  test("acepta un token válido y lee nombre, correo y grupos") {
    val u = validar(token(grupos = Seq("renacer-admin", "familia"))).toOption.get
    assert(u.sub == "abc123" && u.nombre == "María Pérez" && u.email == "maria@renacer.cl")
    assert(u.esAdmin && u.esOperador)
  }

  test("rechaza sin token, vencido, de otro emisor, de otra app, firmado con otra clave o con HS256") {
    assert(Autenticacion.validar(None, procesador).isLeft)
    assert(Autenticacion.validar(Some("Basic abc"), procesador).isLeft)
    assert(validar(token(venceEnSeg = -120)).isLeft)
    assert(validar(token(iss = "https://otro.test/")).isLeft)
    assert(validar(token(aud = "otra-app")).isLeft)
    assert(validar(token(firmante = new RSAKeyGenerator(2048).keyID("prueba").generate())).isLeft)

    val hs = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256),
      new JWTClaimsSet.Builder().issuer(emisor).audience(cliente).subject("x")
        .issueTime(new Date()).expirationTime(new Date(System.currentTimeMillis() + 60000)).build())
    hs.sign(new MACSigner("0123456789abcdef0123456789abcdef"))
    assert(validar(hs.serialize()).isLeft)
  }

  test("reglas por ruta: públicas, de administración y de operación diaria") {
    import Requisito._
    assert(Autenticacion.requisito("OPTIONS", "/api/usuarios") == Publico)
    assert(Autenticacion.requisito("GET", "/api/personas/12/foto") == Publico)
    assert(Autenticacion.requisito("GET", "/api/personas/12") == Operador)
    assert(Autenticacion.requisito("POST", "/api/ingresos/donacion-bienes") == Operador)
    assert(Autenticacion.requisito("POST", "/api/solicitudes") == Operador)
    assert(Autenticacion.requisito("POST", "/api/solicitudes/3/entregar") == Admin)
    assert(Autenticacion.requisito("POST", "/api/ingresos/3/anular") == Admin)
    assert(Autenticacion.requisito("PUT", "/api/egresos/3") == Admin)
    assert(Autenticacion.requisito("GET", "/api/usuarios") == Admin)
    assert(Autenticacion.requisito("DELETE", "/api/personas/3") == Admin)
    assert(Autenticacion.requisito("GET", "/api/cuentas") == Operador)
    assert(Autenticacion.requisito("POST", "/api/cuentas") == Admin)
  }

  test("un miembro no pasa rutas de admin y alguien sin grupos del SGA no pasa ninguna") {
    val miembro = UsuarioSesion("1", "ana", "Ana", "", Set("renacer-miembros"))
    val externo = UsuarioSesion("2", "luis", "Luis", "", Set("familia"))
    assert(Autenticacion.autorizar(miembro, Requisito.Operador).isEmpty)
    assert(Autenticacion.autorizar(miembro, Requisito.Admin).isDefined)
    assert(Autenticacion.autorizar(externo, Requisito.Operador).isDefined)
  }

  // ===== Alcance de la administración de usuarios =====

  /** Authentik simulado: grupos fijos y usuarios en memoria; registra las llamadas que modifican. */
  private class AuthentikFalso(usuarios: Map[Int, JsObject]) extends AuthentikHttp {
    val escrituras = mutable.Buffer.empty[String]
    private val grupos = Seq("renacer-miembros" -> "g-miembros", "renacer-admin" -> "g-admin", "renacer-puerta" -> "g-puerta")
    def enviar(metodo: String, ruta: String, cuerpo: Option[JsValue]): (Int, String) = {
      if (metodo != "GET") escrituras += s"$metodo $ruta"
      if (ruta.startsWith("/core/groups/?")) {
        val nombre = java.net.URLDecoder.decode(ruta.split("search=")(1).takeWhile(_ != '&'), "UTF-8")
        val r = grupos.collect { case (n, pk) if n == nombre => Json.obj("name" -> n, "pk" -> pk) }
        200 -> Json.obj("results" -> r).toString
      } else if (ruta.matches("^/core/users/\\d+/$") && metodo == "GET") {
        usuarios.get(ruta.split("/")(3).toInt).map(u => 200 -> u.toString).getOrElse(404 -> """{"detail":"No encontrado"}""")
      } else 204 -> ""
    }
  }

  private def usuario(pk: Int, username: String, grupos: Seq[String], path: String = "renacer", superuser: Boolean = false) =
    Json.obj("pk" -> pk, "username" -> username, "name" -> username, "email" -> s"$username@x.cl", "is_active" -> true,
      "groups" -> grupos, "path" -> path, "is_superuser" -> superuser, "type" -> "internal")

  test("no toca a akadmin ni a usuarios fuera de Renacer; sí a los de Renacer") {
    val falso = new AuthentikFalso(Map(
      1 -> usuario(1, "akadmin", Seq("g-admins-authentik"), path = "users", superuser = true),
      2 -> usuario(2, "otro", Seq("g-otro"), path = "users"),
      3 -> usuario(3, "intruso", Seq("g-miembros"), path = "users"),
      4 -> usuario(4, "juan", Seq("g-miembros"))
    ))
    val admin = new AuthentikAdmin(falso, "renacer-miembros", "renacer-admin", "renacer-puerta", "renacer")

    Seq(1, 2, 3).foreach { id =>
      val e = intercept[AuthentikError](admin.cambiarPassword(id, "contraseña-larga"))
      assert(e.status == 404, s"usuario $id")
      intercept[AuthentikError](admin.eliminar(id))
      intercept[AuthentikError](admin.definirAdmin(id, valor = true))
    }
    assert(falso.escrituras.isEmpty, falso.escrituras)

    admin.cambiarPassword(4, "contraseña-larga")
    assert(falso.escrituras == Seq("POST /core/users/4/set_password/"))
  }

  test("rechaza contraseñas cortas antes de llamar a Authentik") {
    val falso = new AuthentikFalso(Map(4 -> usuario(4, "juan", Seq("g-miembros"))))
    val admin = new AuthentikAdmin(falso, "renacer-miembros", "renacer-admin", "renacer-puerta", "renacer")
    assert(intercept[AuthentikError](admin.cambiarPassword(4, "corta")).status == 400)
    assert(falso.escrituras.isEmpty)
  }
}
