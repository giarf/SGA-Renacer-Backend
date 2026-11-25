package cl.familiarenacer.sga.mcp

import cl.familiarenacer.sga.modelos._
import cl.familiarenacer.sga.modelos.CampanasJson._
import cl.familiarenacer.sga.repositorios.CampanaRepository
import play.api.libs.json._

import java.text.Normalizer
import scala.util.control.NonFatal

/** Error de uso de una herramienta (argumento inválido, campaña inexistente…): vuelve al cliente como `isError`. */
case class ErrorHerramienta(mensaje: String) extends RuntimeException(mensaje)

case class Herramienta(nombre: String, titulo: String, descripcion: String, esquema: JsObject, ejecutar: JsObject => JsValue,
                       soloLectura: Boolean = true, destructiva: Boolean = false)

/**
 * Herramientas MCP de campañas; comparten el repositorio y las validaciones de la API.
 */
class HerramientasCampanas(repo: CampanaRepository) {
  import HerramientasCampanas._

  val limitePorDefecto = 50
  val limiteMaximo = 200

  val herramientas: List[Herramienta] = List(
    Herramienta("listar_campanas", "Listar campañas",
      "Lista las campañas del SGA (más recientes primero) con id, nombre, fecha, estado, descripción, total de participantes " +
        "y cuántos siguen sin padrino. Úsala primero para obtener el id de una campaña. Filtros opcionales: estado y texto en nombre o descripción.",
      Json.obj("type" -> "object", "properties" -> Json.obj(
        "estado" -> Json.obj("type" -> "string", "enum" -> Json.arr("borrador", "activa", "cerrada"), "description" -> "Solo campañas en este estado."),
        "texto" -> Json.obj("type" -> "string", "description" -> "Texto a buscar en el nombre o la descripción (sin distinguir mayúsculas ni acentos).")
      ), "additionalProperties" -> false),
      listarCampanas),
    Herramienta("ver_campana", "Ver campaña",
      "Muestra una campaña: sus columnas (nombre, tipo y, si es de ficha, qué dato muestra) y sus participantes con beneficiario, " +
        "padrino, gestor del padrino y el valor de cada columna indexado por el NOMBRE de la columna. Devuelve una página " +
        s"(limite por defecto $limitePorDefecto, máximo $limiteMaximo) e indica el total que cumple los filtros. " +
        "Para contar o ver avance usa mejor resumen_campana.",
      Json.obj("type" -> "object", "properties" -> Json.obj(
        "id" -> Json.obj("type" -> "integer", "description" -> "Id de la campaña (ver listar_campanas)."),
        "buscar" -> Json.obj("type" -> "string", "description" -> "Texto en el nombre del beneficiario o del padrino (sin distinguir mayúsculas ni acentos)."),
        "sin_padrino" -> Json.obj("type" -> "boolean", "description" -> "true: solo participantes sin padrino; false: solo con padrino."),
        "columnas" -> Json.obj("type" -> "array", "items" -> Json.obj("type" -> "string"),
          "description" -> "Nombres de columnas a incluir en los valores (por defecto todas)."),
        "limite" -> Json.obj("type" -> "integer", "minimum" -> 1, "maximum" -> limiteMaximo, "description" -> s"Máximo de participantes a devolver (por defecto $limitePorDefecto)."),
        "offset" -> Json.obj("type" -> "integer", "minimum" -> 0, "description" -> "Participantes a saltar, para paginar.")
      ), "required" -> Json.arr("id"), "additionalProperties" -> false),
      verCampana),
    Herramienta("resumen_campana", "Resumen de campaña",
      "Resume el avance de una campaña: total de participantes, con y sin padrino, padrinos con más ahijados, ahijados por gestor " +
        "y, por cada columna, cuántas celdas están llenas, marcadas o vacías (en columnas de mensaje: enviados y pendientes).",
      Json.obj("type" -> "object", "properties" -> Json.obj(
        "id" -> Json.obj("type" -> "integer", "description" -> "Id de la campaña (ver listar_campanas).")
      ), "required" -> Json.arr("id"), "additionalProperties" -> false),
      resumenCampana),
    Herramienta("buscar_en_campanas", "Buscar persona en campañas",
      "Busca a una persona por nombre en todas las campañas y dice en cuáles aparece y con qué rol (beneficiario o padrino), " +
        "junto con su contraparte (el padrino que tiene o los ahijados que apadrina).",
      Json.obj("type" -> "object", "properties" -> Json.obj(
        "texto" -> Json.obj("type" -> "string", "minLength" -> 2, "description" -> "Nombre o parte del nombre (sin distinguir mayúsculas ni acentos).")
      ), "required" -> Json.arr("texto"), "additionalProperties" -> false),
      buscarEnCampanas),
    Herramienta("detalle_campana_api", "Detalle de campaña con versiones",
      "Devuelve el mismo detalle que la API, incluidos IDs, versiones, opciones y valores guardados. Consulta antes de editar.",
      Json.obj("type" -> "object", "properties" -> Json.obj("id" -> Json.obj("type" -> "integer")), "required" -> Json.arr("id"), "additionalProperties" -> false),
      a => Json.toJson(repo.detalle((a \ "id").as[Int])))
  ) ++ EscrituraCampanas.herramientas(repo)

  def definiciones: JsArray = JsArray(herramientas.map { h =>
    Json.obj("name" -> h.nombre, "title" -> h.titulo, "description" -> h.descripcion, "inputSchema" -> h.esquema,
      "annotations" -> Json.obj("title" -> h.titulo, "readOnlyHint" -> h.soloLectura, "destructiveHint" -> h.destructiva,
        "idempotentHint" -> h.soloLectura, "openWorldHint" -> false))
  })

  /** None si la herramienta no existe; si no, el resultado de tools/call. */
  def llamar(nombre: String, argumentos: JsObject): Option[JsObject] = herramientas.find(_.nombre == nombre).map { h =>
    try resultado(h.ejecutar(argumentos), error = false) catch {
      case e: ErrorHerramienta => resultado(JsString(e.mensaje), error = true)
      case e: CampanaError => resultado(JsString(e.mensaje), error = true)
      case e: AsistenciaError => resultado(JsString(e.mensaje), error = true)
      case _: JsResultException => resultado(JsString("Revisa los tipos y valores de los argumentos."), error = true)
      case e: java.sql.SQLException if e.getSQLState == "23505" => resultado(JsString("El beneficiario o el nombre de columna ya está registrado."), error = true)
      case e: java.sql.SQLException if e.getSQLState == "23503" => resultado(JsString("La campaña o la persona ya no existe."), error = true)
      case NonFatal(e) => e.printStackTrace(); resultado(JsString("No se pudo consultar las campañas. Intenta nuevamente."), error = true)
    }
  }

  private def resultado(valor: JsValue, error: Boolean): JsObject = {
    val texto = valor match { case JsString(s) => s; case otro => Json.prettyPrint(otro) }
    Json.obj("content" -> Json.arr(Json.obj("type" -> "text", "text" -> texto)), "isError" -> error)
  }

  // ===== listar_campanas =====

  private def listarCampanas(args: JsObject): JsValue = {
    val estado = texto(args, "estado")
    if (estado.exists(e => !Set("borrador", "activa", "cerrada").contains(e))) throw ErrorHerramienta("El estado debe ser borrador, activa o cerrada.")
    val filtro = texto(args, "texto").map(normalizar).filter(_.nonEmpty)
    val campanas = repo.listar().filter(c => estado.forall(_ == c.estado))
      .filter(c => filtro.forall(f => normalizar(c.nombre).contains(f) || normalizar(c.descripcion).contains(f)))
    Json.obj("total" -> campanas.size, "campanas" -> campanas.map(campanaJson))
  }

  // ===== ver_campana =====

  private def verCampana(args: JsObject): JsValue = {
    val d = repo.detalle(entero(args, "id").getOrElse(throw ErrorHerramienta("Indica el id de la campaña.")))
    val buscar = texto(args, "buscar").map(normalizar).filter(_.nonEmpty)
    val sinPadrino = booleano(args, "sin_padrino")
    val limite = entero(args, "limite").getOrElse(limitePorDefecto)
    val offset = entero(args, "offset").getOrElse(0)
    if (limite < 1 || limite > limiteMaximo) throw ErrorHerramienta(s"El límite debe estar entre 1 y $limiteMaximo.")
    if (offset < 0) throw ErrorHerramienta("El offset no puede ser negativo.")
    val columnas = (args \ "columnas").toOption.filter(_ != JsNull) match {
      case None => d.columnas
      case Some(JsArray(nombres)) if nombres.forall(_.isInstanceOf[JsString]) =>
        val pedidos = nombres.map(_.as[String])
        val faltan = pedidos.filterNot(n => d.columnas.exists(c => normalizar(c.nombre) == normalizar(n)))
        if (faltan.nonEmpty) throw ErrorHerramienta(s"No existen estas columnas: ${faltan.mkString(", ")}. Columnas de la campaña: ${d.columnas.map(_.nombre).mkString(", ")}.")
        d.columnas.filter(c => pedidos.exists(n => normalizar(c.nombre) == normalizar(n)))
      case _ => throw ErrorHerramienta("columnas debe ser una lista de nombres de columna.")
    }
    val filtrados = d.participantes
      .filter(p => sinPadrino.forall(_ == p.padrino.isEmpty))
      .filter(p => buscar.forall(b => normalizar(p.beneficiario.nombreCompleto).contains(b) || p.padrino.exists(a => normalizar(a.nombreCompleto).contains(b))))
    val pagina = filtrados.slice(offset, offset + limite)
    Json.obj(
      "campana" -> campanaJson(d.campana),
      "columnas" -> d.columnas.map(columnaJson),
      "totalParticipantes" -> d.participantes.size,
      "totalFiltrados" -> filtrados.size,
      "offset" -> offset,
      "limite" -> limite,
      "mostrando" -> pagina.size,
      "hayMas" -> (offset + pagina.size < filtrados.size),
      "participantes" -> pagina.map { p =>
        Json.obj(
          "participanteId" -> p.id,
          "beneficiario" -> personaJson(p.beneficiario),
          "padrino" -> p.padrino.map(personaJson),
          "gestorDelPadrino" -> p.padrino.flatMap(_.gestor),
          "valores" -> JsObject(columnas.map(c => c.nombre -> valorCelda(c, p)))
        )
      }
    )
  }

  // ===== resumen_campana =====

  private def resumenCampana(args: JsObject): JsValue = {
    val d = repo.detalle(entero(args, "id").getOrElse(throw ErrorHerramienta("Indica el id de la campaña.")))
    val total = d.participantes.size
    val conPadrino = d.participantes.filter(_.padrino.nonEmpty)
    val porPadrino = conPadrino.groupBy(_.padrino.get.id).values.toList
      .map(ps => (ps.head.padrino.get, ps.size)).sortBy { case (a, n) => (-n, a.nombreCompleto) }
    val porGestor = conPadrino.groupBy(_.padrino.get.gestor.getOrElse("Sin gestor")).toList
      .map { case (g, ps) => Json.obj("gestor" -> g, "padrinos" -> ps.map(_.padrino.get.id).distinct.size, "ahijados" -> ps.size) }
      .sortBy(j => (-(j \ "ahijados").as[Int], (j \ "gestor").as[String]))
    Json.obj(
      "campana" -> campanaJson(d.campana),
      "participantes" -> Json.obj("total" -> total, "conPadrino" -> conPadrino.size, "sinPadrino" -> (total - conPadrino.size),
        "padrinosDistintos" -> porPadrino.size),
      "padrinosConMasAhijados" -> porPadrino.take(10).map { case (a, n) => Json.obj("padrino" -> a.nombreCompleto, "padrinoId" -> a.id, "ahijados" -> n) },
      "porGestor" -> porGestor,
      "columnas" -> d.columnas.map(c => resumenColumna(c, d.participantes))
    )
  }

  private def resumenColumna(c: ColumnaCampana, ps: List[ParticipanteCampana]): JsObject = {
    val base = Json.obj("nombre" -> c.nombre, "tipo" -> c.tipo)
    val valores = ps.map(p => valorCelda(c, p))
    c.tipo match {
      case "boolean" =>
        val marcadas = valores.count(_ == JsBoolean(true))
        val desmarcadas = valores.count(_ == JsBoolean(false))
        base ++ Json.obj("marcadas" -> marcadas, "desmarcadas" -> desmarcadas, "vacias" -> (ps.size - marcadas - desmarcadas))
      case "whatsapp" =>
        val enviados = valores.count(_ == JsString("enviado"))
        val extra = if (c.destinatario == "colaborador")
          Json.obj("pendientesSinPadrino" -> ps.zip(valores).count { case (p, v) => v != JsString("enviado") && p.padrino.isEmpty })
        else Json.obj()
        base ++ Json.obj("destinatario" -> destinatarioLegible(c.destinatario), "enviados" -> enviados, "pendientes" -> (ps.size - enviados)) ++ extra
      case _ =>
        val llenas = valores.count(lleno)
        base ++ c.clave.filter(_ => c.tipo == "ficha").fold(Json.obj())(k => Json.obj("datoFicha" -> k)) ++
          Json.obj("llenas" -> llenas, "vacias" -> (ps.size - llenas))
    }
  }

  // ===== buscar_en_campanas =====

  private def buscarEnCampanas(args: JsObject): JsValue = {
    val buscado = texto(args, "texto").getOrElse("")
    val filtro = normalizar(buscado)
    if (filtro.length < 2) throw ErrorHerramienta("Escribe al menos 2 letras del nombre.")
    case class Aparicion(persona: ContactoCampana, datos: JsObject)
    val apariciones = repo.listar().flatMap { c =>
      val d = repo.detalle(c.id)
      val info = Json.obj("campanaId" -> c.id, "campana" -> c.nombre, "fecha" -> c.fecha.toString, "estado" -> c.estado)
      val comoBeneficiario = d.participantes.filter(p => normalizar(p.beneficiario.nombreCompleto).contains(filtro)).map { p =>
        Aparicion(p.beneficiario, info ++ Json.obj("rol" -> "beneficiario", "participanteId" -> p.id,
          "padrino" -> p.padrino.map(_.nombreCompleto)))
      }
      val comoPadrino = d.participantes.filter(_.padrino.exists(a => normalizar(a.nombreCompleto).contains(filtro)))
        .groupBy(_.padrino.get.id).values.toList.map { ps =>
          Aparicion(ps.head.padrino.get, info ++ Json.obj("rol" -> "padrino", "ahijados" -> ps.map(_.beneficiario.nombreCompleto),
            "gestor" -> ps.head.padrino.get.gestor))
        }
      comoBeneficiario ++ comoPadrino
    }
    val personas = apariciones.groupBy(_.persona.id).values.toList.sortBy(_.head.persona.nombreCompleto).map { as =>
      Json.obj("personaId" -> as.head.persona.id, "nombre" -> as.head.persona.nombreCompleto, "apariciones" -> as.map(_.datos))
    }
    Json.obj("texto" -> buscado, "personasEncontradas" -> personas.size, "personas" -> personas)
  }

  // ===== Formato =====

  private def campanaJson(c: Campana): JsObject = Json.obj("id" -> c.id, "nombre" -> c.nombre, "fecha" -> c.fecha.toString,
    "estado" -> c.estado, "descripcion" -> c.descripcion, "totalParticipantes" -> c.totalParticipantes, "sinPadrino" -> c.sinPadrino)

  private def columnaJson(c: ColumnaCampana): JsObject = {
    val base = Json.obj("id" -> c.id, "nombre" -> c.nombre, "tipo" -> c.tipo, "tipoLegible" -> tipoLegible(c.tipo))
    c.tipo match {
      case "ficha" => base ++ Json.obj("datoFicha" -> c.clave, "soloLectura" -> true)
      case "whatsapp" => base ++ Json.obj("destinatario" -> destinatarioLegible(c.destinatario), "mensaje" -> c.mensaje)
      case "select" => base ++ Json.obj("opciones" -> c.opciones)
      case _ => base
    }
  }

  private def personaJson(c: ContactoCampana): JsObject = Json.obj("id" -> c.id, "nombre" -> c.nombreCompleto, "telefono" -> c.telefono)

  /** Valor de una celda: el guardado, el dato de la ficha o "enviado"/"pendiente" para mensajes; null si está vacía. */
  def valorCelda(c: ColumnaCampana, p: ParticipanteCampana): JsValue = {
    val guardado = p.valores.get(c.id.toString).map(_.valor).filter(_ != JsNull)
    c.tipo match {
      case "ficha" => c.clave.map(_.split('.')).collect {
        case Array("beneficiario", dato) => p.beneficiario.ficha.flatMap(f => (f \ dato).toOption)
        case Array("padrino", dato) => p.padrino.flatMap(_.ficha).flatMap(f => (f \ dato).toOption)
      }.flatten.getOrElse(JsNull)
      case "whatsapp" => JsString(if (guardado.flatMap(v => (v \ "enviado").asOpt[Boolean]).contains(true)) "enviado" else "pendiente")
      case "select" => guardado.flatMap(_.asOpt[String]).flatMap(id => c.opciones.find(_.id == id)).map(o => JsString(o.nombre)).getOrElse(JsNull)
      case _ => guardado.getOrElse(JsNull)
    }
  }
}

object HerramientasCampanas {
  def normalizar(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase.trim.replaceAll("\\s+", " ")

  private def lleno(v: JsValue): Boolean = v match {
    case JsNull => false
    case JsString(s) => s.trim.nonEmpty
    case JsBoolean(b) => b
    case _ => true
  }

  private def tipoLegible(tipo: String): String = tipo match {
    case "boolean" => "casilla (sí/no)"
    case "text" => "texto"
    case "number" => "número"
    case "select" => "selección única"
    case "rating" => "calificación (0 a 5 estrellas)"
    case "whatsapp" => "mensaje de WhatsApp"
    case "ficha" => "dato de la ficha (solo lectura)"
    case otro => otro
  }

  private def destinatarioLegible(d: String): String = if (d == "colaborador") "padrino" else d

  private def texto(args: JsObject, campo: String): Option[String] = (args \ campo).toOption.filter(_ != JsNull).map {
    case JsString(s) => s.trim
    case _ => throw ErrorHerramienta(s"$campo debe ser texto.")
  }

  private def booleano(args: JsObject, campo: String): Option[Boolean] = (args \ campo).toOption.filter(_ != JsNull).map {
    case JsBoolean(b) => b
    case JsString("true") => true
    case JsString("false") => false
    case _ => throw ErrorHerramienta(s"$campo debe ser true o false.")
  }

  private def entero(args: JsObject, campo: String): Option[Int] = (args \ campo).toOption.filter(_ != JsNull).map {
    case JsNumber(n) if n.isValidInt => n.toInt
    case JsString(s) if s.trim.toIntOption.nonEmpty => s.trim.toInt
    case _ => throw ErrorHerramienta(s"$campo debe ser un número entero.")
  }
}
