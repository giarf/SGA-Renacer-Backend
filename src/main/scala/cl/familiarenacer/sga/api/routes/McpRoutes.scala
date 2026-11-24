package cl.familiarenacer.sga.api.routes

import cl.familiarenacer.sga.auth.Autenticacion
import cl.familiarenacer.sga.mcp.{AutorizacionMcp, ConfigMcp, HerramientasCampanas, ServidorMcp}
import cl.familiarenacer.sga.repositorios.CampanaRepository
import play.api.libs.json._

/**
 * Servidor MCP de solo lectura sobre campañas (ver docs/mcp.md). /mcp exige sesión de operación
 * diaria como el resto del API; los metadatos OAuth del recurso (RFC 9728) son públicos.
 */
class McpRoutes(repo: CampanaRepository, autorizacion: AutorizacionMcp = new AutorizacionMcp(Autenticacion.issuer, Autenticacion.clientId))
  (implicit cc: castor.Context, log: cask.Logger) extends cask.Routes {
  private val servidor = new ServidorMcp(new HerramientasCampanas(repo))
  private val publico = Seq("Access-Control-Allow-Origin" -> "*")

  private def json(data: JsValue, status: Int = 200, extra: Seq[(String, String)] = Nil): cask.Response[String] =
    cask.Response(data.toString, statusCode = status, headers = Seq("Content-Type" -> "application/json") ++ extra)

  @cask.post(ConfigMcp.ruta)
  def mcp(request: cask.Request) = {
    val salida = servidor.procesar(request.text())
    salida.cuerpo match {
      case Some(cuerpo) => json(cuerpo, salida.status)
      case None => cask.Response("", statusCode = salida.status)
    }
  }

  // Sin SSE: no hay flujo de eventos que abrir ni sesión que cerrar.
  @cask.get(ConfigMcp.ruta)
  def mcpGet() = json(Json.obj("error" -> "Este servidor MCP no ofrece SSE; usa POST."), 405, Seq("Allow" -> "POST"))
  @cask.delete(ConfigMcp.ruta)
  def mcpDelete() = json(Json.obj("error" -> "Este servidor MCP no usa sesiones."), 405, Seq("Allow" -> "POST"))

  @cask.get(ConfigMcp.rutaMetadatos)
  def metadatos() = metadatosRecurso()
  @cask.get(ConfigMcp.rutaMetadatos + ConfigMcp.ruta)
  def metadatosMcp() = metadatosRecurso()

  private def metadatosRecurso() = {
    json(ConfigMcp.metadatos(ConfigMcp.urlPublica(), autorizacion.base()), extra = publico :+ ("Cache-Control" -> "max-age=3600"))
  }

  // Fachada de servidor de autorización (ver AutorizacionMcp): metadatos y registro dinámico.
  @cask.get(AutorizacionMcp.rutaMetadatosAS)
  def metadatosAS() = json(autorizacion.metadatos(), extra = publico :+ ("Cache-Control" -> "max-age=3600"))
  @cask.get(AutorizacionMcp.rutaMetadatosOidc)
  def metadatosOidc() = metadatosAS()

  @cask.post(AutorizacionMcp.rutaRegistro)
  def registrar(request: cask.Request) = {
    val (status, cuerpo) = autorizacion.registrar(request.text())
    json(cuerpo, status, publico :+ ("Cache-Control" -> "no-store"))
  }

  // Preflight: FiltroAutenticacion lo responde antes de llegar aquí; cask exige que la ruta exista para OPTIONS.
  @cask.options(ConfigMcp.ruta)
  def mcpOptions() = cask.Response("", statusCode = 204)
  @cask.options(ConfigMcp.rutaMetadatos)
  def metadatosOptions() = cask.Response("", statusCode = 204)
  @cask.options(ConfigMcp.rutaMetadatos + ConfigMcp.ruta)
  def metadatosMcpOptions() = cask.Response("", statusCode = 204)
  @cask.options(AutorizacionMcp.rutaMetadatosAS)
  def metadatosASOptions() = cask.Response("", statusCode = 204)
  @cask.options(AutorizacionMcp.rutaMetadatosOidc)
  def metadatosOidcOptions() = cask.Response("", statusCode = 204)
  @cask.options(AutorizacionMcp.rutaRegistro)
  def registrarOptions() = cask.Response("", statusCode = 204)

  initialize()
}
