package cl.familiarenacer.sga

import cl.familiarenacer.sga.api.routes.McpRoutes
import cl.familiarenacer.sga.auth.FiltroAutenticacion

/**
 * Levanta /mcp con el filtro de autenticación real y campañas en memoria, para probar con curl
 * sin base de datos: `AUTH_MODE=off MCP_PUBLIC_URL=http://localhost:8080/mcp sbt "Test/runMain cl.familiarenacer.sga.McpLocal"`.
 * Sin MCP_PUBLIC_URL, los metadatos y el 401 anuncian la URL de producción.
 */
object McpLocal extends cask.Main {
  override def port = sys.env.get("SGA_API_PORT").map(_.toInt).getOrElse(8080)
  override def mainDecorators = Seq(new FiltroAutenticacion())
  val allRoutes = Seq(new McpRoutes(new CampanaRepositoryFalso))
}
