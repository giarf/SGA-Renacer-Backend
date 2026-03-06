package cl.familiarenacer.sga.api.routes

import cl.familiarenacer.sga.api.ApiSupport
import cl.familiarenacer.sga.modelos._
import cl.familiarenacer.sga.repositorios.CuentaFinancieraRepository
import play.api.libs.json._

class CuentasRoutes(cuentaRepo: CuentaFinancieraRepository)(implicit cc: castor.Context, log: cask.Logger) extends cask.Routes with ApiSupport {

  // ===== ENDPOINTS =====
  case class CrearCuentaRequest(nombre: String)
  implicit val crearCuentaRequestFormat: OFormat[CrearCuentaRequest] = Json.format[CrearCuentaRequest]

  @cask.options("/api/cuentas")
  def cuentasOptions() = corsOptions()

  @cask.options("/api/cuentas/:id")
  def cuentaByIdOptions(id: Int) = corsOptions()

  @cask.options("/api/cuentas/:id/movimientos")
  def cuentaMovimientosOptions(id: Int) = corsOptions()

  @cask.get("/api/cuentas")
  def listarCuentas() = {
    try {
      val cuentas = cuentaRepo.listarCuentas()
      respond(Json.toJson(cuentas))
    } catch {
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  @cask.post("/api/cuentas")
  def crearCuenta(request: cask.Request) = {
    try {
      val body = Json.parse(request.text()).as[CrearCuentaRequest]
      val nombre = body.nombre.trim
      if (nombre.isEmpty) throw new IllegalArgumentException("El nombre de la cuenta es obligatorio.")
      val cuentaNueva = CuentaFinanciera(id = 0, nombre = Some(nombre), saldoActual = Some(BigDecimal(0)))
      val idGenerado = cuentaRepo.crearCuenta(cuentaNueva)
      respond(Json.obj("id" -> idGenerado, "mensaje" -> "Cuenta creada exitosamente"), 201)
    } catch {
      case e: IllegalArgumentException =>
        respond(Json.obj("error" -> e.getMessage), 400)
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  @cask.get("/api/cuentas/:id")
  def obtenerCuenta(id: Int) = {
    try {
      cuentaRepo.obtenerCuenta(id) match {
        case Some(cuenta) => respond(Json.toJson(cuenta))
        case None => respond(Json.obj("error" -> s"Cuenta con ID $id no encontrada"), 404)
      }
    } catch {
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  @cask.put("/api/cuentas/:id")
  def actualizarCuenta(id: Int, request: cask.Request) = {
    try {
      val body = Json.parse(request.text())
      val nombre = (body \ "nombre").as[String]
      val rowsUpdated = cuentaRepo.actualizarCuenta(id, nombre)
      if (rowsUpdated > 0) {
        respond(Json.obj("mensaje" -> "Cuenta actualizada exitosamente"))
      } else {
        respond(Json.obj("error" -> s"No se encontró la cuenta con ID $id"), 404)
      }
    } catch {
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  @cask.delete("/api/cuentas/:id")
  def eliminarCuenta(id: Int) = {
    try {
      val eliminado = cuentaRepo.eliminarCuenta(id)
      if (eliminado) {
        respond(Json.obj("mensaje" -> "Cuenta eliminada exitosamente"))
      } else {
        respond(Json.obj("error" -> "No se encontró la cuenta"), 404)
      }
    } catch {
      case e: org.postgresql.util.PSQLException if e.getSQLState == "23503" =>
        respond(Json.obj("error" -> "No se puede eliminar la cuenta porque tiene movimientos asociados"), 409)
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  @cask.get("/api/cuentas/:id/movimientos")
  def obtenerMovimientosCuenta(id: Int) = {
    try {
      val (ingresos, egresos) = cuentaRepo.obtenerMovimientos(id)
      respond(Json.obj(
        "ingresos" -> Json.toJson(ingresos),
        "egresos" -> Json.toJson(egresos)
      ))
    } catch {
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  initialize()
}
