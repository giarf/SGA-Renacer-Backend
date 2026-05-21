package cl.familiarenacer.sga.api.routes

import cl.familiarenacer.sga.api.ApiSupport
import cl.familiarenacer.sga.repositorios.UbicacionRepository
import play.api.libs.json.Json

class UbicacionesRoutes(ubicacionRepo: UbicacionRepository)(implicit cc: castor.Context, log: cask.Logger) extends cask.Routes with ApiSupport {
  @cask.options("/api/ubicaciones/regiones")
  def regionesOptions() = corsOptions()

  @cask.options("/api/ubicaciones/comunas")
  def comunasOptions() = corsOptions()

  @cask.get("/api/ubicaciones/regiones")
  def listarRegiones() = {
    try respond(Json.toJson(ubicacionRepo.listarRegiones()))
    catch {
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  @cask.get("/api/ubicaciones/comunas")
  def listarComunas(regionId: Option[Int] = None) = {
    try respond(Json.toJson(ubicacionRepo.listarComunas(regionId)))
    catch {
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  initialize()
}
