package cl.familiarenacer.sga.mcp

import play.api.libs.json._

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.time.{Duration, Instant}
import scala.util.Try

/**
 * Fachada de servidor de autorización para clientes MCP que no permiten escribir un client_id.
 *
 * Authentik no ofrece registro dinámico (RFC 7591). El backend publica unos metadatos de servidor
 * de autorización (RFC 8414) con los endpoints de Authentik y un `registration_endpoint` propio
 * que siempre entrega el client_id público del SGA. Los tokens los sigue emitiendo Authentik.
 *
 * `issuer` es la base del backend y no la de Authentik: los clientes (SDK de MCP) exigen que
 * coincida con la URL que aparece en `authorization_servers`. Authentik no envía `iss` en la
 * redirección de vuelta, así que esa diferencia no rompe el flujo.
 */
class AutorizacionMcp(
  emisorAuthentik: String,
  clientId: String,
  leerAuthentik: String => Option[JsObject] = AutorizacionMcp.leerHttp,
  ahora: () => Instant = () => Instant.now()
) {
  import AutorizacionMcp._

  private val duracionCache = Duration.ofMinutes(30)
  @volatile private var cache: Option[(Instant, JsObject)] = None

  /** Base pública del backend (esquema + host), derivada de la URL pública del MCP. */
  def base(urlMcp: String = ConfigMcp.urlPublica()): String = {
    val u = URI.create(urlMcp)
    s"${u.getScheme}://${u.getRawAuthority}"
  }

  private def authentik(): JsObject = cache match {
    case Some((hasta, doc)) if ahora().isBefore(hasta) => doc
    case anterior =>
      leerAuthentik(emisorAuthentik.stripSuffix("/") + "/.well-known/openid-configuration") match {
        case Some(doc) => cache = Some(ahora().plus(duracionCache) -> doc); doc
        // Si Authentik no responde, se usa la última copia o los endpoints conocidos.
        case None => anterior.map(_._2).getOrElse(respaldo(emisorAuthentik))
      }
  }

  def metadatos(baseBackend: String = base()): JsObject = {
    val a = authentik()
    def endpoint(nombre: String): Option[String] = (a \ nombre).asOpt[String].orElse((respaldo(emisorAuthentik) \ nombre).asOpt[String])
    JsObject(Seq(
      "issuer" -> Some(baseBackend),
      "authorization_endpoint" -> endpoint("authorization_endpoint"),
      "token_endpoint" -> endpoint("token_endpoint"),
      "jwks_uri" -> endpoint("jwks_uri"),
      "revocation_endpoint" -> endpoint("revocation_endpoint"),
      "userinfo_endpoint" -> endpoint("userinfo_endpoint"),
      "end_session_endpoint" -> endpoint("end_session_endpoint"),
      "registration_endpoint" -> Some(baseBackend + rutaRegistro)
    ).collect { case (k, Some(v)) => k -> JsString(v) }) ++ Json.obj(
      "authentik_issuer" -> emisorAuthentik,
      "response_types_supported" -> Json.arr("code"),
      "grant_types_supported" -> Json.arr("authorization_code", "refresh_token"),
      "code_challenge_methods_supported" -> Json.arr("S256"),
      "token_endpoint_auth_methods_supported" -> Json.arr("none"),
      "scopes_supported" -> ConfigMcp.scopes,
      "subject_types_supported" -> Json.arr("public"),
      "id_token_signing_alg_values_supported" -> Json.arr("RS256")
    )
  }

  /** Registro dinámico falso (RFC 7591): valida las redirect_uris y devuelve siempre el cliente público del SGA. */
  def registrar(cuerpo: String): (Int, JsObject) =
    Try(Json.parse(cuerpo)).toOption match {
      case Some(pedido: JsObject) =>
        (pedido \ "redirect_uris").toOption match {
          case Some(JsArray(uris)) if uris.nonEmpty && uris.forall(_.isInstanceOf[JsString]) =>
            val lista = uris.map(_.as[String]).toList
            lista.filterNot(esLoopback) match {
              case Nil =>
                val extra = Seq("client_name", "scope", "client_uri", "software_id", "software_version")
                  .flatMap(k => (pedido \ k).asOpt[String].map(k -> JsString(_)))
                201 -> (JsObject(extra) ++ Json.obj(
                  "client_id" -> clientId,
                  "client_id_issued_at" -> ahora().getEpochSecond,
                  "token_endpoint_auth_method" -> "none",
                  "redirect_uris" -> lista,
                  "grant_types" -> Json.arr("authorization_code", "refresh_token"),
                  "response_types" -> Json.arr("code")
                ))
              case malas => 400 -> error("invalid_redirect_uri",
                s"Solo se aceptan redirecciones locales (http://127.0.0.1:<puerto>/... o http://localhost:<puerto>/...): ${malas.mkString(", ")}")
            }
          case _ => 400 -> error("invalid_redirect_uri", "Falta redirect_uris: una lista con al menos una URL de redirección.")
        }
      case _ => 400 -> error("invalid_client_metadata", "El cuerpo debe ser un objeto JSON.")
    }
}

object AutorizacionMcp {
  val rutaRegistro = "/oauth/register"
  val rutaMetadatosAS = "/.well-known/oauth-authorization-server"
  val rutaMetadatosOidc = "/.well-known/openid-configuration"

  def error(codigo: String, descripcion: String): JsObject = Json.obj("error" -> codigo, "error_description" -> descripcion)

  /** http, host 127.0.0.1 o localhost, puerto explícito y sin fragmento. */
  def esLoopback(uri: String): Boolean = Try(URI.create(uri)).toOption.exists { u =>
    u.getScheme == "http" && Option(u.getHost).exists(h => h == "127.0.0.1" || h.equalsIgnoreCase("localhost")) &&
      u.getPort > 0 && u.getRawUserInfo == null && u.getRawFragment == null
  }

  /** Endpoints de Authentik para un proveedor `<host>/application/o/<slug>/`. */
  def respaldo(emisor: String): JsObject = {
    val e = emisor.stripSuffix("/") + "/"
    val u = URI.create(e)
    val o = s"${u.getScheme}://${u.getRawAuthority}/application/o/"
    Json.obj(
      "authorization_endpoint" -> s"${o}authorize/", "token_endpoint" -> s"${o}token/",
      "userinfo_endpoint" -> s"${o}userinfo/", "revocation_endpoint" -> s"${o}revoke/",
      "jwks_uri" -> s"${e}jwks/", "end_session_endpoint" -> s"${e}end-session/"
    )
  }

  private lazy val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()

  def leerHttp(url: String): Option[JsObject] = Try {
    val r = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5)).header("Accept", "application/json").GET().build(),
      HttpResponse.BodyHandlers.ofString())
    if (r.statusCode() == 200) Json.parse(r.body()).asOpt[JsObject] else None
  }.toOption.flatten
}
