package cl.familiarenacer.sga.api.routes

import cl.familiarenacer.sga.api.ApiSupport
import cl.familiarenacer.sga.catalogo.{OpenFoodFactsHttp, ProductoExterno}
import cl.familiarenacer.sga.modelos._
import cl.familiarenacer.sga.repositorios.InventarioRepository
import play.api.libs.json._

class CatalogoRoutes(
  inventarioRepo: InventarioRepository,
  productoExterno: ProductoExterno = new OpenFoodFactsHttp()
)(implicit cc: castor.Context, log: cask.Logger) extends cask.Routes with ApiSupport {

  // ===== DTOs =====

  case class CrearItemRequest(
    nombre: String,
    categoria: Option[String],
    unidadMedidaEstandar: Option[String],
    precioReferencia: Option[BigDecimal],
    codigoBarras: Option[String] = None,
    origenDatos: Option[String] = None
  )
  implicit val crearItemFormat: OFormat[CrearItemRequest] = Json.format[CrearItemRequest]

  case class ActualizarItemRequest(
    id: Int,
    nombre: Option[String],
    categoria: Option[String],
    unidadMedidaEstandar: Option[String],
    precioReferencia: Option[BigDecimal]
  )
  implicit val actualizarItemFormat: OFormat[ActualizarItemRequest] = Json.format[ActualizarItemRequest]

  case class UtilitariosResponse(categorias: List[String], unidades: List[String])
  implicit val utilitariosResponseFormat: OFormat[UtilitariosResponse] = Json.format[UtilitariosResponse]

  // ===== ENDPOINTS =====

  @cask.options("/api/catalogo")
  def catalogoOptions() = corsOptions()

  @cask.options("/api/catalogo/:id")
  def catalogoByIdOptions(id: Int) = corsOptions()

  @cask.get("/api/catalogo")
  def listarCatalogo() = {
    try {
      val items = inventarioRepo.listarTodosItems()
      respond(Json.toJson(items))
    } catch {
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  @cask.post("/api/catalogo")
  def registrarItemCatalogo(request: cask.Request) = {
    try {
      val body = Json.parse(request.text()).as[CrearItemRequest]
      if (body.nombre.trim.isEmpty) {
        respond(Json.obj("error" -> "El nombre del ítem es obligatorio"), 400)
      } else {
        val codigo = body.codigoBarras.map(_.trim).filter(_.nonEmpty).map(inventarioRepo.validarCodigo)
        val origen = body.origenDatos.map(_.trim.toLowerCase).filter(_.nonEmpty)
        origen.filterNot(OrigenDatos.validos).foreach { o =>
          throw new IllegalArgumentException(s"origenDatos inválido: $o (use ${OrigenDatos.validos.toSeq.sorted.mkString(", ")})")
        }
        val nuevoItem = ItemCatalogo(
          id = 0,
          nombre = Some(body.nombre.trim),
          categoria = body.categoria.map(_.trim).filter(_.nonEmpty),
          unidadMedidaEstandar = body.unidadMedidaEstandar.map(_.trim).filter(_.nonEmpty),
          stockActual = None,
          valorTotalStock = None,
          precioPromedioPonderado = None,
          precioReferencia = body.precioReferencia,
          codigoBarras = codigo,
          origenDatos = origen.orElse(Some(OrigenDatos.Manual))
        )
        val idGenerado = inventarioRepo.registrarItem(nuevoItem)
        respond(Json.obj("id" -> idGenerado, "mensaje" -> "Ítem registrado exitosamente en el catálogo"), 201)
      }
    } catch {
      case e: Exception if errorCliente(e).isDefined => errorCliente(e).get
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  @cask.put("/api/catalogo/:id")
  def actualizarItemCatalogo(id: Int, request: cask.Request) = {
    try {
      val json = Json.parse(request.text())
      val body = json.as[ActualizarItemRequest]
      // Ausente = se conserva; null o "" = se borra; texto = se valida y asigna.
      val codigo: Option[Option[String]] = (json \ "codigoBarras").toOption.map {
        case JsString(c) if c.trim.nonEmpty => Some(inventarioRepo.validarCodigo(c))
        case JsString(_) | JsNull => None
        case _ => throw new IllegalArgumentException("codigoBarras debe ser texto")
      }
      val filasActualizadas = inventarioRepo.actualizarItemBasico(
        id, body.nombre, body.categoria, body.unidadMedidaEstandar, body.precioReferencia, codigo
      )
      if (filasActualizadas > 0) {
        respond(Json.obj("mensaje" -> "Ítem actualizado exitosamente", "filasActualizadas" -> filasActualizadas))
      } else {
        respond(Json.obj("error" -> s"No se encontró el ítem con ID $id"), 404)
      }
    } catch {
      case e: Exception if errorCliente(e).isDefined => errorCliente(e).get
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  @cask.options("/api/catalogo/codigo/:codigo")
  def codigoOptions(codigo: String) = corsOptions()

  /** Busca un código de barras en el catálogo local y, si no está, en Open Food Facts (sin guardar nada). */
  @cask.get("/api/catalogo/codigo/:codigo")
  def buscarPorCodigo(codigo: String) = {
    CodigoBarras.normalizar(codigo) match {
      case None => respond(Json.obj("error" -> "Código de barras inválido"), 400)
      case Some(c) =>
        try {
          inventarioRepo.buscarPorCodigo(c) match {
            case Some(item) =>
              respond(Json.obj("fuente" -> "catalogo", "item" -> Json.toJson(item), "sugerencia" -> JsNull))
            case None =>
              productoExterno.buscar(c) match {
                case Some(sugerencia) =>
                  respond(Json.obj("fuente" -> "openfoodfacts", "item" -> JsNull, "sugerencia" -> Json.toJson(sugerencia)))
                case None =>
                  respond(Json.obj("error" -> "Producto no encontrado", "codigoBarras" -> c), 404)
              }
          }
        } catch {
          case e: Exception =>
            e.printStackTrace()
            respond(Json.obj("error" -> e.getMessage), 500)
        }
    }
  }

  /** 400 por datos inválidos y 409 por código de barras duplicado (incluida la carrera contra el índice único). */
  private def errorCliente(e: Throwable): Option[cask.Response[String]] = e match {
    case d: CodigoBarrasDuplicadoException =>
      Some(respond(Json.obj("error" -> d.getMessage, "codigoBarras" -> d.codigo), 409))
    case i: IllegalArgumentException => Some(respond(Json.obj("error" -> i.getMessage), 400))
    case j: JsResultException => Some(respond(Json.obj("error" -> s"JSON inválido: ${j.getMessage}"), 400))
    case p: org.postgresql.util.PSQLException
        if p.getSQLState == "23505" && Option(p.getMessage).exists(_.contains("codigo_barras")) =>
      Some(respond(Json.obj("error" -> "El código de barras ya está asignado a otro ítem del catálogo"), 409))
    case other if other.getCause != null && other.getCause != other => errorCliente(other.getCause)
    case _ => None
  }

  @cask.options("/api/catalogo/buscar")
  def buscarCatalogoOptions() = corsOptions()

  @cask.options("/api/catalogo/utilitarios")
  def utilitariosOptions() = corsOptions()

  @cask.options("/api/catalogo/utilitarios/categorias")
  def categoriasOptions() = corsOptions()

  @cask.get("/api/catalogo/buscar")
  def buscarItems(q: String) = {
    try {
      val resultados = inventarioRepo.buscarItems(q)
      respond(Json.toJson(resultados))
    } catch {
      case e: Exception =>
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  @cask.get("/api/catalogo/utilitarios")
  def catalogoUtilitarios() = {
    try {
      val (cats, units) = inventarioRepo.listarCategoriasUnidades()
      respond(Json.toJson(UtilitariosResponse(cats, units)))
    } catch {
      case e: Exception =>
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  @cask.get("/api/catalogo/utilitarios/categorias")
  def obtenerCategorias() = {
    try {
      val categorias = inventarioRepo.obtenerCategoriasUnicas()
      respond(Json.toJson(categorias))
    } catch {
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  initialize()
}
