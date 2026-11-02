package cl.familiarenacer.sga.auth

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.source.{JWKSource, JWKSourceBuilder}
import com.nimbusds.jose.proc.{JWSVerificationKeySelector, SecurityContext}
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.proc.{DefaultJWTClaimsVerifier, DefaultJWTProcessor}

import java.net.URL
import scala.jdk.CollectionConverters._
import scala.util.Try

/** Persona que hace la llamada, sacada del token de Authentik ya verificado. */
case class UsuarioSesion(sub: String, username: String, nombre: String, email: String, grupos: Set[String]) {
  def esAdmin: Boolean = grupos.exists(Autenticacion.gruposAdmin.contains)
  def esOperador: Boolean = esAdmin || grupos.exists(Autenticacion.gruposOperacion.contains)
  /** Texto para auditoría: "Nombre <correo>". */
  def etiqueta: String = if (email.nonEmpty) s"$nombre <$email>" else nombre
}

/** Qué exige una ruta: nada (pública), sesión de operación diaria o sesión de administrador. */
sealed trait Requisito
object Requisito {
  case object Publico extends Requisito
  case object Operador extends Requisito
  case object Admin extends Requisito
}

/**
 * Verifica los tokens de acceso que el frontend recibe de Authentik (proveedor "sga-renacer").
 *
 * El token es un JWT firmado con RS256; la clave pública sale del JWKS del proveedor y se
 * cachea. Se revisa firma, emisor, audiencia (client id), vencimiento y que tenga `sub`.
 * Los grupos vienen en el claim `groups` (mapping "profile" de Authentik).
 */
object Autenticacion {

  val issuer: String = sys.env.getOrElse("AUTHENTIK_ISSUER", "https://auth.slaksis.com/application/o/sga-renacer/")
  val clientId: String = sys.env.getOrElse("AUTHENTIK_CLIENT_ID", "sga-renacer-frontend")

  /** enforce: rechaza sin token válido. log: deja pasar y solo avisa (para desplegar sin cortar). off: no revisa. */
  val modo: String = sys.env.getOrElse("AUTH_MODE", "enforce").trim.toLowerCase

  val gruposAdmin: Set[String] = Set(sys.env.getOrElse("AUTHENTIK_ADMIN_GROUP", "renacer-admin"), "renacer-admins")
  val gruposOperacion: Set[String] = Set(sys.env.getOrElse("AUTHENTIK_MEMBER_GROUP", "renacer-miembros"))

  private lazy val fuenteClaves: JWKSource[SecurityContext] =
    JWKSourceBuilder.create[SecurityContext](new URL(issuer.stripSuffix("/") + "/jwks/")).retrying(true).build()

  private lazy val procesador = crearProcesador(fuenteClaves, issuer, clientId)

  /** Separado para poder probarlo con claves locales. */
  def crearProcesador(fuente: JWKSource[SecurityContext], emisor: String, audiencia: String): DefaultJWTProcessor[SecurityContext] = {
    val proc = new DefaultJWTProcessor[SecurityContext]()
    proc.setJWSKeySelector(new JWSVerificationKeySelector[SecurityContext](
      Set(JWSAlgorithm.RS256, JWSAlgorithm.ES256).asJava, fuente
    ))
    proc.setJWTClaimsSetVerifier(new DefaultJWTClaimsVerifier[SecurityContext](
      audiencia,
      new JWTClaimsSet.Builder().issuer(emisor).build(),
      Set("sub", "exp", "iat").asJava
    ))
    proc
  }

  /** Lee "Authorization: Bearer <jwt>" y devuelve el usuario o el motivo del rechazo. */
  def validar(header: Option[String], proc: DefaultJWTProcessor[SecurityContext] = procesador): Either[String, UsuarioSesion] = {
    val token = header.map(_.trim).filter(_.regionMatches(true, 0, "Bearer ", 0, 7)).map(_.drop(7).trim).filter(_.nonEmpty)
    token match {
      case None => Left("Falta iniciar sesión")
      case Some(jwt) =>
        Try(proc.process(jwt, null)).toEither.left.map(_ => "Sesión inválida o vencida").map(usuarioDesde)
    }
  }

  def usuarioDesde(claims: JWTClaimsSet): UsuarioSesion = {
    def texto(nombre: String) = Option(claims.getStringClaim(nombre)).getOrElse("")
    val grupos = Option(claims.getStringListClaim("groups")).map(_.asScala.toSet).getOrElse(Set.empty)
    UsuarioSesion(
      sub = claims.getSubject,
      username = texto("preferred_username"),
      nombre = Option(texto("name")).filter(_.nonEmpty).getOrElse(texto("preferred_username")),
      email = texto("email"),
      grupos = grupos
    )
  }

  // ===== Reglas por ruta =====

  private val publicas: Seq[(String, scala.util.matching.Regex)] = Seq(
    // Se cargan como <img> o enlace directo, que no pueden enviar el token.
    "GET" -> "^/api/personas/\\d+/foto$".r,
    "GET" -> "^/api/ingresos/compra/boleta/\\d+/download$".r
  )

  private val soloAdmin: Seq[(Set[String], scala.util.matching.Regex)] = Seq(
    Set("GET", "POST", "PUT", "PATCH", "DELETE") -> "^/api/usuarios(/.*)?$".r,
    Set("PUT") -> "^/api/ingresos/\\d+$".r,
    Set("POST") -> "^/api/ingresos/\\d+/anular$".r,
    Set("PUT", "DELETE") -> "^/api/egresos/\\d+$".r,
    Set("POST") -> "^/api/egresos/\\d+/anular$".r,
    Set("POST") -> "^/api/solicitudes/\\d+/entregar$".r,
    Set("POST", "PUT", "PATCH", "DELETE") -> "^/api/cuentas(/.*)?$".r,
    Set("PUT") -> "^/api/catalogo/\\d+$".r,
    // Borrar cualquier cosa es de administración.
    Set("DELETE") -> "^/api/.*$".r
  )

  def requisito(metodo: String, ruta: String): Requisito = {
    val m = metodo.toUpperCase
    if (m == "OPTIONS") Requisito.Publico
    else if (publicas.exists { case (pm, r) => pm == m && r.matches(ruta) }) Requisito.Publico
    else if (soloAdmin.exists { case (ms, r) => ms.contains(m) && r.matches(ruta) }) Requisito.Admin
    else Requisito.Operador
  }

  /** Permiso para un usuario ya autenticado: None si puede pasar, o el mensaje de rechazo. */
  def autorizar(usuario: UsuarioSesion, req: Requisito): Option[String] = req match {
    case Requisito.Publico => None
    case Requisito.Operador => if (usuario.esOperador) None else Some("Tu cuenta no tiene acceso al SGA")
    case Requisito.Admin => if (usuario.esAdmin) None else Some("Solo un administrador puede hacer esto")
  }

  // ===== Usuario de la petición en curso =====

  private val actualTL = new ThreadLocal[Option[UsuarioSesion]] { override def initialValue() = None }
  def actual: Option[UsuarioSesion] = actualTL.get()
  private[auth] def conUsuario[T](usuario: Option[UsuarioSesion])(f: => T): T = {
    actualTL.set(usuario)
    try f finally actualTL.remove()
  }
}
