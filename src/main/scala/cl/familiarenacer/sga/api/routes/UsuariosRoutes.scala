package cl.familiarenacer.sga.api.routes

import cl.familiarenacer.sga.api.ApiSupport
import cl.familiarenacer.sga.auth.{Autenticacion, UsuarioSesion}
import cl.familiarenacer.sga.repositorios.AuditoriaRepository
import cl.familiarenacer.sga.usuarios.{AuthentikAdmin, AuthentikError, UsuarioRenacer}
import play.api.libs.json._

/**
 * Usuarios de Renacer en Authentik (pantalla Usuarios del SGA). Solo administradores.
 * Authentik ejecuta los cambios con la cuenta de servicio; la persona real queda en la auditoría.
 */
class UsuariosRoutes(admin: AuthentikAdmin, auditoria: AuditoriaRepository)(implicit cc: castor.Context, log: cask.Logger)
  extends cask.Routes with ApiSupport {

  case class CrearUsuarioRequest(
    name: String,
    email: String,
    username: Option[String] = None,
    password: Option[String] = None,
    isAdmin: Boolean = false,
    isGateAdmin: Boolean = false
  )
  implicit val crearUsuarioFormat: OFormat[CrearUsuarioRequest] = Json.using[Json.WithDefaultValues].format[CrearUsuarioRequest]

  case class ActualizarUsuarioRequest(name: Option[String] = None, email: Option[String] = None, isActive: Option[Boolean] = None)
  implicit val actualizarUsuarioFormat: OFormat[ActualizarUsuarioRequest] = Json.using[Json.WithDefaultValues].format[ActualizarUsuarioRequest]

  case class PasswordRequest(password: String)
  implicit val passwordFormat: OFormat[PasswordRequest] = Json.format[PasswordRequest]

  case class ValorRequest(valor: Boolean)
  implicit val valorFormat: OFormat[ValorRequest] = Json.format[ValorRequest]

  /**
   * Exige un administrador aunque AUTH_MODE sea "log": sin persona identificada no hay auditoría,
   * y estas rutas manejan contraseñas.
   */
  private def comoAdmin(accion: UsuarioSesion => cask.Response[String]): cask.Response[String] =
    Autenticacion.actual match {
      case None => respond(Json.obj("error" -> "Falta iniciar sesión"), 401)
      case Some(u) if !u.esAdmin => respond(Json.obj("error" -> "Solo un administrador puede administrar usuarios"), 403)
      case Some(u) =>
        try accion(u)
        catch {
          case e: AuthentikError => respond(Json.obj("error" -> e.getMessage), e.status)
          case e: JsResultException => respond(Json.obj("error" -> s"Datos inválidos: ${e.getMessage}"), 400)
          case e: Exception =>
            e.printStackTrace()
            respond(Json.obj("error" -> "No se pudo completar la operación con Authentik"), 502)
        }
    }

  private def auditar(actor: UsuarioSesion, accion: String, usuario: UsuarioRenacer, detalle: JsObject = Json.obj()): Unit =
    auditoria.registrar(actor.sub, actor.nombre, Some(actor.email), accion, "usuario_authentik",
      Some(usuario.id.toString), Some(usuario.username), if (detalle.fields.isEmpty) None else Some(detalle))

  // ===== OPTIONS =====

  @cask.options("/api/usuarios")
  def usuariosOptions() = corsOptions()
  @cask.options("/api/usuarios/existe")
  def existeOptions() = corsOptions()
  @cask.options("/api/usuarios/auditoria")
  def auditoriaOptions() = corsOptions()
  @cask.options("/api/usuarios/:id")
  def usuarioOptions(id: Int) = corsOptions()
  @cask.options("/api/usuarios/:id/password")
  def passwordOptions(id: Int) = corsOptions()
  @cask.options("/api/usuarios/:id/admin")
  def adminOptions(id: Int) = corsOptions()
  @cask.options("/api/usuarios/:id/puerta")
  def puertaOptions(id: Int) = corsOptions()

  // ===== ENDPOINTS =====

  @cask.get("/api/usuarios")
  def listar() = comoAdmin { _ => respond(Json.toJson(admin.listar())) }

  @cask.get("/api/usuarios/existe")
  def existe(username: String) = comoAdmin { _ => respond(Json.obj("existe" -> admin.usernameExiste(username))) }

  /** Quién cambió qué en los usuarios, más reciente primero. */
  @cask.get("/api/usuarios/auditoria")
  def verAuditoria(limite: Int = 100) = comoAdmin { _ =>
    respond(Json.toJson(auditoria.listar(Some("usuario_authentik"), limite)))
  }

  @cask.post("/api/usuarios")
  def crear(request: cask.Request) = comoAdmin { actor =>
    val body = Json.parse(request.text()).as[CrearUsuarioRequest]
    val creado = admin.crear(body.name, body.email, body.username, body.password, body.isAdmin, body.isGateAdmin)
    auditar(actor, "crear", creado, Json.obj(
      "nombre" -> creado.name, "email" -> creado.email, "admin" -> creado.isAdmin,
      "puerta" -> creado.isGateAdmin, "conContraseña" -> body.password.isDefined
    ))
    respond(Json.toJson(creado), 201)
  }

  @cask.patch("/api/usuarios/:id")
  def actualizar(id: Int, request: cask.Request) = comoAdmin { actor =>
    val body = Json.parse(request.text()).as[ActualizarUsuarioRequest]
    val antes = admin.obtener(id)
    val despues = admin.actualizar(id, body.name, body.email, body.isActive)
    val cambios = Seq(
      Option.when(antes.name != despues.name)("nombre" -> Json.obj("antes" -> antes.name, "despues" -> despues.name)),
      Option.when(antes.email != despues.email)("email" -> Json.obj("antes" -> antes.email, "despues" -> despues.email)),
      Option.when(antes.isActive != despues.isActive)("activo" -> Json.obj("antes" -> antes.isActive, "despues" -> despues.isActive))
    ).flatten
    if (cambios.nonEmpty) auditar(actor, "editar", despues, JsObject(cambios.map { case (k, v) => k -> (v: JsValue) }))
    respond(Json.toJson(despues))
  }

  @cask.post("/api/usuarios/:id/password")
  def cambiarPassword(id: Int, request: cask.Request) = comoAdmin { actor =>
    val body = Json.parse(request.text()).as[PasswordRequest]
    admin.cambiarPassword(id, body.password)
    auditar(actor, "cambiar_contraseña", admin.obtener(id))
    respond(Json.obj("mensaje" -> "Contraseña actualizada"))
  }

  @cask.post("/api/usuarios/:id/admin")
  def definirAdmin(id: Int, request: cask.Request) = comoAdmin { actor =>
    val body = Json.parse(request.text()).as[ValorRequest]
    if (!body.valor && actor.sub.nonEmpty && admin.obtener(id).username.equalsIgnoreCase(actor.username)) {
      respond(Json.obj("error" -> "No puedes quitarte el permiso de administrador a ti mismo"), 400)
    } else {
      val antes = admin.obtener(id)
      val despues = admin.definirAdmin(id, body.valor)
      if (antes.isAdmin != despues.isAdmin) auditar(actor, if (body.valor) "dar_admin" else "quitar_admin", despues)
      respond(Json.toJson(despues))
    }
  }

  @cask.post("/api/usuarios/:id/puerta")
  def definirPuerta(id: Int, request: cask.Request) = comoAdmin { actor =>
    val body = Json.parse(request.text()).as[ValorRequest]
    val antes = admin.obtener(id)
    val despues = admin.definirPuerta(id, body.valor)
    if (antes.isGateAdmin != despues.isGateAdmin) auditar(actor, if (body.valor) "dar_puerta" else "quitar_puerta", despues)
    respond(Json.toJson(despues))
  }

  @cask.delete("/api/usuarios/:id")
  def eliminar(id: Int) = comoAdmin { actor =>
    if (admin.obtener(id).username.equalsIgnoreCase(actor.username)) {
      respond(Json.obj("error" -> "No puedes eliminar tu propio usuario"), 400)
    } else {
      val borrado = admin.eliminar(id)
      auditar(actor, "eliminar", borrado, Json.obj("nombre" -> borrado.name, "email" -> borrado.email))
      respond(Json.obj("mensaje" -> "Usuario eliminado"))
    }
  }

  initialize()
}
