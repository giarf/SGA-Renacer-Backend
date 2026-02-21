package cl.familiarenacer.sga.api.routes

import cl.familiarenacer.sga.api.ApiSupport
import cl.familiarenacer.sga.modelos._
import cl.familiarenacer.sga.repositorios.{DonacionRepository, InventarioRepository}
import play.api.libs.json._

class IngresosRoutes(
  donacionRepo: DonacionRepository,
  inventarioRepo: InventarioRepository
)(implicit cc: castor.Context, log: cask.Logger) extends cask.Routes with ApiSupport {

  // ===== DTOs =====

  case class DonacionRequest(ingreso: IngresoRecurso, donacion: IngresoDonacion, pecuniario: IngresoPecuniario)
  implicit val donacionRequestFormat: OFormat[DonacionRequest] = Json.format[DonacionRequest]

  case class ItemDonacionRequest(
    id: Int,
    itemCatalogoId: Int,
    nombre: String,
    categoria: Option[String],
    unidad: Option[String],
    cantidad: BigDecimal,
    precio: BigDecimal
  )
  implicit val itemDonacionFormat: OFormat[ItemDonacionRequest] = Json.format[ItemDonacionRequest]

  case class RegistrarDonacionBienesRequest(
    ingreso: IngresoRecurso,
    donacion: IngresoDonacion,
    items: List[ItemDonacionRequest]
  )
  implicit val registrarDonacionBienesFormat: OFormat[RegistrarDonacionBienesRequest] = Json.format[RegistrarDonacionBienesRequest]

  case class RegistrarCompraRequest(
    ingreso: IngresoRecurso,
    compra: IngresoCompra,
    detalles: List[DetalleIngresoRecurso]
  )
  implicit val registrarCompraFormat: OFormat[RegistrarCompraRequest] = Json.format[RegistrarCompraRequest]

  case class RegistrarPecuniarioRequest(
    ingreso: IngresoRecurso,
    pecuniario: IngresoPecuniario
  )
  implicit val registrarPecuniarioFormat: OFormat[RegistrarPecuniarioRequest] = Json.format[RegistrarPecuniarioRequest]

  case class RegistrarSubvencionRequest(
    ingreso: IngresoRecurso,
    subvencion: IngresoSubvencion,
    pecuniario: Option[IngresoPecuniario]
  )
  implicit val registrarSubvencionFormat: OFormat[RegistrarSubvencionRequest] = Json.format[RegistrarSubvencionRequest]

  // ===== ENDPOINTS =====

  @cask.options("/api/ingresos/donacion")
  def donacionOptions() = corsOptions()

  @cask.post("/api/ingresos/donacion")
  def registrarDonacion(request: cask.Request) = {
    try {
      val body = Json.parse(request.text()).as[DonacionRequest]
      val id = donacionRepo.registrarDonacionDinero(body.ingreso, body.donacion, body.pecuniario)
      respond(Json.obj("id_ingreso" -> id, "status" -> "registrado"), 201)
    } catch {
      case e: Exception =>
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  @cask.options("/api/ingresos/donacion-bienes")
  def donacionBienesOptions() = corsOptions()

  @cask.post("/api/ingresos/donacion-bienes")
  def registrarDonacionBienes(request: cask.Request) = {
    try {
      val body = Json.parse(request.text()).as[RegistrarDonacionBienesRequest]
      val detallesInput = body.items.map { item =>
        DetalleDonacionInput(
          detalle = DetalleIngresoRecurso(
            id = 0, ingresoId = None,
            itemCatalogoId = if (item.itemCatalogoId > 0) Some(item.itemCatalogoId) else None,
            cantidad = Some(item.cantidad),
            precioUnitarioIngreso = Some(item.precio)
          ),
          nombreItem = item.nombre,
          categoria = item.categoria,
          unidadMedida = item.unidad
        )
      }
      val id = inventarioRepo.registrarDonacionCompleta(body.ingreso, body.donacion, detallesInput)
      respond(Json.obj("id_ingreso" -> id, "mensaje" -> "Donación de bienes registrada exitosamente"), 201)
    } catch {
      case e: IllegalArgumentException =>
        respond(Json.obj("error" -> e.getMessage), 400)
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  @cask.options("/api/ingresos/compra")
  def compraOptions() = corsOptions()

  @cask.post("/api/ingresos/compra")
  def registrarCompra(request: cask.Request) = {
    try {
      val body = Json.parse(request.text()).as[RegistrarCompraRequest]
      val id = donacionRepo.registrarCompra(body.ingreso, body.compra, body.detalles)
      respond(Json.obj("id_ingreso" -> id, "mensaje" -> "Compra registrada exitosamente"), 201)
    } catch {
      case e: IllegalArgumentException =>
        respond(Json.obj("error" -> e.getMessage), 400)
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  @cask.options("/api/ingresos/subvencion")
  def subvencionOptions() = corsOptions()

  @cask.post("/api/ingresos/subvencion")
  def registrarSubvencion(request: cask.Request) = {
    try {
      val body = Json.parse(request.text()).as[RegistrarSubvencionRequest]
      val id = donacionRepo.registrarSubvencion(body.ingreso, body.subvencion, body.pecuniario)
      respond(Json.obj("id_ingreso" -> id, "mensaje" -> "Subvención registrada exitosamente"), 201)
    } catch {
      case e: IllegalArgumentException =>
        respond(Json.obj("error" -> e.getMessage), 400)
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  initialize()
}
