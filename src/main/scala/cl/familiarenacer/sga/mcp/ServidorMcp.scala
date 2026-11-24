package cl.familiarenacer.sga.mcp

import play.api.libs.json._

import java.net.URI

/** Dónde se publica el servidor MCP. MCP_PUBLIC_URL la cambia (p. ej. en local); por defecto, la de producción. */
object ConfigMcp {
  val ruta = "/mcp"
  val rutaMetadatos = "/.well-known/oauth-protected-resource"
  val scopes = Seq("openid", "profile", "email", "offline_access")
  val urlProduccion = "https://api.familiarenacer.cl/mcp"

  /** URL pública de /mcp, que va en `resource` y en el header WWW-Authenticate. */
  def urlPublica(env: Map[String, String] = sys.env): String =
    env.get("MCP_PUBLIC_URL").map(_.trim.stripSuffix("/")).filter(_.nonEmpty).getOrElse(urlProduccion)

  /** RFC 9728 §3.1: el documento va en /.well-known/oauth-protected-resource + ruta del recurso. */
  def urlMetadatos(recurso: String): String = {
    val u = URI.create(recurso)
    val ruta = Option(u.getRawPath).filter(p => p.nonEmpty && p != "/").getOrElse("")
    s"${u.getScheme}://${u.getRawAuthority}$rutaMetadatos$ruta"
  }

  def metadatos(recurso: String, emisor: String): JsObject = Json.obj(
    "resource" -> recurso,
    "authorization_servers" -> Json.arr(emisor),
    "scopes_supported" -> scopes,
    "bearer_methods_supported" -> Json.arr("header"),
    "resource_name" -> "SGA Renacer · Campañas"
  )

  def esRutaMcp(ruta: String): Boolean = ruta == this.ruta || ruta.startsWith(this.ruta + "/")
}

/**
 * MCP sobre Streamable HTTP, sin SSE: cada POST trae un mensaje JSON-RPC 2.0 y la respuesta es
 * application/json. Las notificaciones y respuestas del cliente se aceptan con 202 sin cuerpo.
 */
class ServidorMcp(herramientas: HerramientasCampanas) {
  import ServidorMcp._

  def procesar(cuerpo: String): Salida =
    scala.util.Try(Json.parse(cuerpo)).toOption match {
      case None => Salida(400, Some(error(JsNull, -32700, "El cuerpo no es JSON válido.")))
      case Some(_: JsArray) => Salida(400, Some(error(JsNull, -32600, "Este servidor no acepta lotes JSON-RPC; envía un mensaje por petición.")))
      case Some(m: JsObject) => procesarMensaje(m)
      case Some(_) => Salida(400, Some(error(JsNull, -32600, "Mensaje JSON-RPC inválido.")))
    }

  private def procesarMensaje(m: JsObject): Salida = {
    val id = (m \ "id").toOption
    val metodo = (m \ "method").asOpt[String]
    if ((m \ "jsonrpc").asOpt[String] != Some("2.0")) Salida(400, Some(error(id.getOrElse(JsNull), -32600, "Falta jsonrpc: \"2.0\".")))
    else (metodo, id) match {
      // Notificación (sin id) o respuesta del cliente a una petición nuestra: no llevan respuesta.
      case (Some(_), None) => Salida(202, None)
      case (None, Some(_)) if m.keys.exists(k => k == "result" || k == "error") => Salida(202, None)
      case (Some(nombre), Some(idValor)) if idValor.isInstanceOf[JsString] || idValor.isInstanceOf[JsNumber] =>
        val params = (m \ "params").asOpt[JsObject].getOrElse(Json.obj())
        Salida(200, Some(responder(idValor, nombre, params)))
      case _ => Salida(400, Some(error(id.getOrElse(JsNull), -32600, "Mensaje JSON-RPC inválido.")))
    }
  }

  private def responder(id: JsValue, metodo: String, params: JsObject): JsObject = metodo match {
    case "initialize" =>
      val version: String = (params \ "protocolVersion").asOpt[String].filter(v => versiones.contains(v)).getOrElse(versiones.head)
      ok(id, Json.obj(
        "protocolVersion" -> version,
        "capabilities" -> Json.obj("tools" -> Json.obj("listChanged" -> false)),
        "serverInfo" -> Json.obj("name" -> "sga-renacer-campanas", "title" -> "SGA Renacer · Campañas", "version" -> "0.1.0"),
        "instructions" -> instrucciones
      ))
    case "ping" => ok(id, Json.obj())
    case "tools/list" => ok(id, Json.obj("tools" -> herramientas.definiciones))
    case "tools/call" =>
      (params \ "name").asOpt[String] match {
        case None => error(id, -32602, "Falta el nombre de la herramienta.")
        case Some(nombre) =>
          (params \ "arguments").toOption.filter(_ != JsNull).getOrElse(Json.obj()) match {
            case args: JsObject => herramientas.llamar(nombre, args).map(ok(id, _))
              .getOrElse(error(id, -32602, s"Herramienta desconocida: $nombre"))
            case _ => error(id, -32602, "arguments debe ser un objeto.")
          }
      }
    case otro => error(id, -32601, s"Método no soportado: $otro")
  }
}

object ServidorMcp {
  /** La primera es la que se ofrece si el cliente pide una que no conocemos. */
  val versiones = Seq("2025-06-18", "2025-03-26", "2024-11-05")

  case class Salida(status: Int, cuerpo: Option[JsObject])

  def ok(id: JsValue, resultado: JsObject): JsObject = Json.obj("jsonrpc" -> "2.0", "id" -> id, "result" -> resultado)
  def error(id: JsValue, codigo: Int, mensaje: String): JsObject =
    Json.obj("jsonrpc" -> "2.0", "id" -> id, "error" -> Json.obj("code" -> codigo, "message" -> mensaje))

  val instrucciones: String =
    """Servidor de solo lectura con las campañas del SGA de la Fundación Familia Renacer.
      |
      |Qué es una campaña: una actividad con fecha y estado (borrador, activa o cerrada; una cerrada ya no se edita), por ejemplo
      |un apadrinamiento de Navidad. Sus participantes son los beneficiarios (los ahijados). A cada beneficiario se le puede
      |asignar un padrino (un colaborador que, por ejemplo, hace un regalo); un mismo padrino puede apadrinar a varios
      |beneficiarios. El gestor del padrino es el miembro de Renacer que se encarga de ese padrino (sale de su ficha).
      |
      |Cada campaña tiene columnas, que son una planilla por participante:
      |- Personalizadas: casilla sí/no (boolean), texto o número, p. ej. "Regalo recibido" o "Gustos y preferencias".
      |  La plantilla de apadrinamiento trae columnas como Ficha enviada, Regalo recibido, Regalo entregado y Agradecimiento enviado.
      |- De solo lectura con datos de la ficha (tipo ficha): muestran un dato de la persona, p. ej. beneficiario.edad o padrino.telefono.
      |- De mensaje (tipo whatsapp): un texto común para enviar al beneficiario o al padrino; cada celda queda enviado o pendiente.
      |
      |Cómo usar las herramientas:
      |1. listar_campanas para encontrar el id (puedes filtrar por estado o texto).
      |2. resumen_campana para preguntas de avance o conteo (cuántos sin padrino, cuántos regalos recibidos, mensajes pendientes,
      |   qué padrinos o gestores tienen más ahijados). Es más barato y exacto que contar a mano.
      |3. ver_campana para el detalle de personas: filtra con buscar, sin_padrino y columnas, y pagina con limite/offset;
      |   revisa totalFiltrados y hayMas antes de concluir que no hay más filas.
      |4. buscar_en_campanas para saber en qué campañas aparece una persona y con qué rol (beneficiario o padrino).
      |Los valores de las columnas vienen por el nombre de la columna; null significa celda vacía.
      |Estas herramientas no modifican nada: para editar hay que usar el SGA (https://sga.familiarenacer.cl).
      |Los datos son personales (nombres, teléfonos, edades de niños): úsalos solo para la gestión de la campaña.""".stripMargin
}
