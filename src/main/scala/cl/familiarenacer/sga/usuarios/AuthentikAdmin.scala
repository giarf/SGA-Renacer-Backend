package cl.familiarenacer.sga.usuarios

import play.api.libs.json._

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Duration

/** Usuario de Renacer tal como lo ve la pantalla Usuarios del SGA. */
case class UsuarioRenacer(
  id: Int,
  username: String,
  name: String,
  email: String,
  isActive: Boolean,
  isAdmin: Boolean,
  isGateAdmin: Boolean,
  lastLogin: Option[String]
)
object UsuarioRenacer {
  implicit val format: OFormat[UsuarioRenacer] = Json.format[UsuarioRenacer]
}

/** Error de Authentik con el código HTTP que conviene devolver al frontend. */
class AuthentikError(val status: Int, mensaje: String) extends RuntimeException(mensaje)

/** Llamadas HTTP a la API de Authentik (separado para poder simularlo en tests). */
trait AuthentikHttp {
  /** Devuelve (código, cuerpo). */
  def enviar(metodo: String, ruta: String, cuerpo: Option[JsValue]): (Int, String)
}

class AuthentikHttpReal(baseUrl: String, token: String) extends AuthentikHttp {
  private val cliente = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()

  def enviar(metodo: String, ruta: String, cuerpo: Option[JsValue]): (Int, String) = {
    if (token.isEmpty) throw new AuthentikError(503, "Falta AUTHENTIK_ADMIN_TOKEN en el backend")
    val publicador = cuerpo.map(c => HttpRequest.BodyPublishers.ofString(c.toString)).getOrElse(HttpRequest.BodyPublishers.noBody())
    val req = HttpRequest.newBuilder(URI.create(s"${baseUrl.stripSuffix("/")}/api/v3$ruta"))
      .timeout(Duration.ofSeconds(10))
      .header("Authorization", s"Bearer $token")
      .header("Accept", "application/json")
      .header("Content-Type", "application/json")
      .method(metodo, publicador)
      .build()
    val resp = cliente.send(req, HttpResponse.BodyHandlers.ofString())
    (resp.statusCode(), Option(resp.body()).getOrElse(""))
  }
}

/**
 * Administración de usuarios de Renacer con la cuenta de servicio `sga-backend`.
 *
 * Esa cuenta puede tocar cualquier usuario de Authentik (sus permisos de usuario son globales),
 * así que la regla de alcance la impone este código: solo se modifican usuarios que están en
 * el grupo de miembros y que no son superusuarios ni cuentas de servicio. Así ni el SGA ni un
 * administrador del SGA pueden cambiarle la contraseña a `akadmin`.
 */
class AuthentikAdmin(
  http: AuthentikHttp,
  grupoMiembros: String = sys.env.getOrElse("AUTHENTIK_MEMBER_GROUP", "renacer-miembros"),
  grupoAdmin: String = sys.env.getOrElse("AUTHENTIK_ADMIN_GROUP", "renacer-admin"),
  grupoPuerta: String = sys.env.getOrElse("AUTHENTIK_GATE_ADMIN_GROUP", "renacer-puerta"),
  rutaUsuarios: String = sys.env.getOrElse("AUTHENTIK_USER_PATH", "renacer")
) {

  private case class Grupos(miembros: String, admin: String, puerta: String)
  @volatile private var gruposCache: Option[Grupos] = None

  private def enc(v: String) = URLEncoder.encode(v, StandardCharsets.UTF_8)

  private def llamar(metodo: String, ruta: String, cuerpo: Option[JsValue] = None): JsValue = {
    val (status, texto) = http.enviar(metodo, ruta, cuerpo)
    val json = if (texto.trim.isEmpty) JsNull else scala.util.Try(Json.parse(texto)).getOrElse(JsString(texto))
    if (status >= 200 && status < 300) json
    else {
      val detalle = json match {
        case o: JsObject =>
          (o \ "detail").asOpt[String]
            .orElse((o \ "non_field_errors").asOpt[Seq[String]].map(_.mkString(", ")))
            .getOrElse(o.fields.map { case (k, v) => s"$k: ${v match { case JsArray(a) => a.flatMap(_.asOpt[String]).mkString(", "); case x => x.toString }}" }.mkString("; "))
        case JsString(s) => s
        case _ => ""
      }
      val codigo = if (status == 401 || status == 403) 502 else if (status == 404) 404 else 400
      throw new AuthentikError(codigo, s"Authentik respondió $status${if (detalle.nonEmpty) s": $detalle" else ""}")
    }
  }

  private def resultados(json: JsValue): Seq[JsObject] =
    (json \ "results").asOpt[Seq[JsObject]].orElse(json.asOpt[Seq[JsObject]]).getOrElse(Nil)

  private def grupos(): Grupos = gruposCache.getOrElse {
    def pk(nombre: String): String = {
      val json = llamar("GET", s"/core/groups/?search=${enc(nombre)}&include_users=false&page_size=50")
      resultados(json).find(g => (g \ "name").asOpt[String].exists(_.equalsIgnoreCase(nombre)))
        .flatMap(g => (g \ "pk").asOpt[String])
        .getOrElse(throw new AuthentikError(502, s"No existe el grupo $nombre en Authentik"))
    }
    val g = Grupos(pk(grupoMiembros), pk(grupoAdmin), pk(grupoPuerta))
    gruposCache = Some(g)
    g
  }

  private def gruposDe(u: JsObject): Set[String] =
    (u \ "groups").asOpt[Seq[String]].getOrElse(Nil).map(_.toLowerCase).toSet

  private def normalizar(u: JsObject, g: Grupos): UsuarioRenacer = {
    val gs = gruposDe(u)
    UsuarioRenacer(
      id = (u \ "pk").as[Int],
      username = (u \ "username").asOpt[String].getOrElse(""),
      name = (u \ "name").asOpt[String].getOrElse(""),
      email = (u \ "email").asOpt[String].getOrElse(""),
      isActive = (u \ "is_active").asOpt[Boolean].getOrElse(false),
      isAdmin = gs.contains(g.admin.toLowerCase),
      isGateAdmin = gs.contains(g.puerta.toLowerCase),
      lastLogin = (u \ "last_login").asOpt[String]
    )
  }

  /**
   * Regla de alcance: está en el grupo de miembros de Renacer y no es superusuario ni cuenta de servicio.
   * La carpeta (path) no cuenta: hay miembros fuera de "renacer". Los nuevos sí se crean en esa carpeta.
   */
  def esDeRenacer(u: JsObject): Boolean = {
    val g = grupos()
    gruposDe(u).contains(g.miembros.toLowerCase) &&
      !(u \ "is_superuser").asOpt[Boolean].getOrElse(false) &&
      (u \ "type").asOpt[String].forall(t => t == "internal" || t == "external")
  }

  /** Trae el usuario y falla con 404 si no es de Renacer (no se distingue de "no existe"). */
  private def usuarioRenacer(id: Int): JsObject = {
    val u = scala.util.Try(llamar("GET", s"/core/users/$id/").as[JsObject]).toOption
    u.filter(esDeRenacer).getOrElse(throw new AuthentikError(404, s"No existe un usuario de Renacer con ID $id"))
  }

  def obtener(id: Int): UsuarioRenacer = normalizar(usuarioRenacer(id), grupos())

  def listar(): List[UsuarioRenacer] = {
    val g = grupos()
    def pagina(n: Int, acc: List[UsuarioRenacer]): List[UsuarioRenacer] = {
      val json = llamar("GET", s"/core/users/?groups_by_pk=${enc(g.miembros)}&ordering=username&page_size=100&page=$n")
      val usuarios = resultados(json).filter(esDeRenacer).map(normalizar(_, g)).toList
      val siguiente = (json \ "pagination" \ "next").asOpt[Int].filter(_ > 0)
      siguiente match {
        case Some(p) if p != n => pagina(p, acc ++ usuarios)
        case _ => acc ++ usuarios
      }
    }
    pagina(1, Nil)
  }

  def usernameExiste(username: String): Boolean = {
    val limpio = username.trim.toLowerCase
    limpio.nonEmpty && resultados(llamar("GET", s"/core/users/?username=${enc(limpio)}")).exists(u =>
      (u \ "username").asOpt[String].exists(_.equalsIgnoreCase(limpio))
    )
  }

  def crear(nombre: String, email: String, username: Option[String], password: Option[String], admin: Boolean, puerta: Boolean): UsuarioRenacer = {
    val correo = email.trim.toLowerCase
    val usuario = username.map(_.trim.toLowerCase).filter(_.nonEmpty).getOrElse(correo.takeWhile(_ != '@'))
    if (nombre.trim.isEmpty) throw new AuthentikError(400, "El nombre es obligatorio")
    if (!correo.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) throw new AuthentikError(400, "El correo no es válido")
    if (usuario.isEmpty || !usuario.matches("^[a-z0-9._@+-]+$")) throw new AuthentikError(400, "El usuario solo puede tener letras, números y . _ - @ +")
    password.foreach(validarPassword)
    if (usernameExiste(usuario)) throw new AuthentikError(409, s"Ya existe el usuario $usuario")

    val g = grupos()
    val creado = llamar("POST", "/core/users/", Some(Json.obj(
      "username" -> usuario,
      "name" -> nombre.trim,
      "email" -> correo,
      "is_active" -> true,
      "path" -> rutaUsuarios,
      "type" -> "internal",
      "groups" -> Json.arr(g.miembros)
    ))).as[JsObject]
    val id = (creado \ "pk").as[Int]
    password.foreach(p => llamar("POST", s"/core/users/$id/set_password/", Some(Json.obj("password" -> p))))
    if (admin) cambiarGrupo(g.admin, id, agregar = true)
    if (puerta) cambiarGrupo(g.puerta, id, agregar = true)
    obtener(id)
  }

  def actualizar(id: Int, nombre: Option[String], email: Option[String], activo: Option[Boolean]): UsuarioRenacer = {
    usuarioRenacer(id)
    val cambios = Json.obj() ++
      nombre.map(_.trim).filter(_.nonEmpty).fold(Json.obj())(n => Json.obj("name" -> n)) ++
      email.map(_.trim.toLowerCase).fold(Json.obj()) { e =>
        if (!e.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) throw new AuthentikError(400, "El correo no es válido")
        Json.obj("email" -> e)
      } ++
      activo.fold(Json.obj())(a => Json.obj("is_active" -> a))
    if (cambios.fields.nonEmpty) llamar("PATCH", s"/core/users/$id/", Some(cambios))
    obtener(id)
  }

  def cambiarPassword(id: Int, password: String): Unit = {
    validarPassword(password)
    usuarioRenacer(id)
    llamar("POST", s"/core/users/$id/set_password/", Some(Json.obj("password" -> password)))
  }

  def definirAdmin(id: Int, valor: Boolean): UsuarioRenacer = definirGrupo(id, grupos().admin, valor)

  def definirPuerta(id: Int, valor: Boolean): UsuarioRenacer = definirGrupo(id, grupos().puerta, valor)

  /** Solo llama a Authentik si hay que cambiar algo (agregar dos veces o quitar a quien no está da error). */
  private def definirGrupo(id: Int, grupoPk: String, valor: Boolean): UsuarioRenacer = {
    val u = usuarioRenacer(id)
    if (gruposDe(u).contains(grupoPk.toLowerCase) != valor) cambiarGrupo(grupoPk, id, valor)
    obtener(id)
  }

  /** Devuelve el usuario borrado (para la auditoría). */
  def eliminar(id: Int): UsuarioRenacer = {
    val u = obtener(id)
    llamar("DELETE", s"/core/users/$id/")
    u
  }

  private def cambiarGrupo(grupoPk: String, usuarioId: Int, agregar: Boolean): Unit =
    llamar("POST", s"/core/groups/$grupoPk/${if (agregar) "add_user" else "remove_user"}/", Some(Json.obj("pk" -> usuarioId)))

  private def validarPassword(p: String): Unit =
    if (p.length < 8) throw new AuthentikError(400, "La contraseña debe tener al menos 8 caracteres")
}
