package cl.familiarenacer.sga.api.routes

import cl.familiarenacer.sga.api.ApiSupport
import cl.familiarenacer.sga.modelos._
import cl.familiarenacer.sga.repositorios.InventarioRepository
import play.api.libs.json._

class CatalogoRoutes(inventarioRepo: InventarioRepository)(implicit cc: castor.Context, log: cask.Logger) extends cask.Routes with ApiSupport {

  // ===== DTOs =====

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
      val item = Json.parse(request.text()).as[ItemCatalogo]
      if (item.nombre.isEmpty || item.nombre.exists(_.trim.isEmpty)) {
        respond(Json.obj("error" -> "El nombre del ítem es obligatorio"), 400)
      } else {
        val idGenerado = inventarioRepo.registrarItem(item)
        respond(Json.obj("id" -> idGenerado, "mensaje" -> "Ítem registrado exitosamente en el catálogo"), 201)
      }
    } catch {
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  @cask.put("/api/catalogo/:id")
  def actualizarItemCatalogo(id: Int, request: cask.Request) = {
    try {
      val body = Json.parse(request.text()).as[ActualizarItemRequest]
      val filasActualizadas = inventarioRepo.actualizarItemBasico(
        id, body.nombre, body.categoria, body.unidadMedidaEstandar, body.precioReferencia
      )
      if (filasActualizadas > 0) {
        respond(Json.obj("mensaje" -> "Ítem actualizado exitosamente", "filasActualizadas" -> filasActualizadas))
      } else {
        respond(Json.obj("error" -> s"No se encontró el ítem con ID $id"), 404)
      }
    } catch {
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

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
