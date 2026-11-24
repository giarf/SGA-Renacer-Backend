package cl.familiarenacer.sga.api.routes

import cl.familiarenacer.sga.auth.Autenticacion
import cl.familiarenacer.sga.mcp.{ConfigMcp, HerramientasCampanas, ServidorMcp}
import cl.familiarenacer.sga.repositorios.CampanaRepository
import play.api.libs.json._

/**
 * Servidor MCP de solo lectura sobre campañas (ver docs/mcp.md). /mcp exige sesión de operación
 * diaria como el resto del API; los metadatos OAuth del recurso (RFC 9728) son públicos.
 */
class McpRoutes(repo: CampanaRepository)(implicit cc: castor.Context, log: cask.Logger) extends cask.Routes {
  private val servidor = new ServidorMcp(new HerramientasCampanas(repo))

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
  def metadatos(request: cask.Request) = metadatosRecurso(request)
  @cask.get(ConfigMcp.rutaMetadatos + ConfigMcp.ruta)
  def metadatosMcp(request: cask.Request) = metadatosRecurso(request)

  private def metadatosRecurso(request: cask.Request) = {
    val recurso = ConfigMcp.urlRecurso(McpRoutes.host(request.exchange), McpRoutes.proto(request.exchange))
    json(ConfigMcp.metadatos(recurso, Autenticacion.issuer), extra = Seq("Access-Control-Allow-Origin" -> "*", "Cache-Control" -> "max-age=3600"))
  }

  initialize()
}

object McpRoutes {
  def host(ex: io.undertow.server.HttpServerExchange): Option[String] =
    Option(ex.getRequestHeaders.getFirst("X-Forwarded-Host")).orElse(Option(ex.getRequestHeaders.getFirst("Host")))
  def proto(ex: io.undertow.server.HttpServerExchange): Option[String] =
    Option(ex.getRequestHeaders.getFirst("X-Forwarded-Proto")).orElse(Option(ex.getRequestScheme))
}
