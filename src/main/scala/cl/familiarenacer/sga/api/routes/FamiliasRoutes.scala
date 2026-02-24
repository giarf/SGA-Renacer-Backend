package cl.familiarenacer.sga.api.routes

import cl.familiarenacer.sga.api.ApiSupport
import cl.familiarenacer.sga.modelos._
import cl.familiarenacer.sga.repositorios.FamiliaRepository
import play.api.libs.json._

class FamiliasRoutes(familiaRepo: FamiliaRepository)(implicit cc: castor.Context, log: cask.Logger) extends cask.Routes with ApiSupport {

  // ===== DTOs =====

  case class AgregarMiembroRequest(personaId: Int)
  implicit val agregarMiembroFormat: OFormat[AgregarMiembroRequest] = Json.format[AgregarMiembroRequest]

  // ===== ENDPOINTS =====

  @cask.options("/api/familias")
  def familiasOptions() = corsOptions()

  @cask.options("/api/familias/:id")
  def familiaByIdOptions(id: Int) = corsOptions()

  @cask.options("/api/familias/:id/beneficiarios")
  def familiaBeneficiariosOptions(id: Int) = corsOptions()

  @cask.get("/api/familias")
  def listarFamilias() = {
    try {
      val familias = familiaRepo.listarFamilias()
      respond(Json.toJson(familias))
    } catch {
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  @cask.post("/api/familias")
  def crearFamilia(request: cask.Request) = {
    try {
      val body = Json.parse(request.text()).as[Familia]
      val idGenerado = familiaRepo.crearFamilia(body)
      respond(Json.obj("id" -> idGenerado, "mensaje" -> "Familia creada exitosamente"), 201)
    } catch {
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  @cask.get("/api/familias/:id")
  def obtenerFamilia(id: Int) = {
    try {
      familiaRepo.obtenerFamilia(id) match {
        case Some(familia) => respond(Json.toJson(familia))
        case None => respond(Json.obj("error" -> s"Familia con ID $id no encontrada"), 404)
      }
    } catch {
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  @cask.put("/api/familias/:id")
  def actualizarFamilia(id: Int, request: cask.Request) = {
    try {
      val body = Json.parse(request.text()).as[Familia]
      val rowsUpdated = familiaRepo.actualizarFamilia(id, body)
      if (rowsUpdated > 0) {
        respond(Json.obj("mensaje" -> "Familia actualizada exitosamente"))
      } else {
        respond(Json.obj("error" -> s"No se encontró la familia con ID $id"), 404)
      }
    } catch {
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  @cask.delete("/api/familias/:id")
  def eliminarFamilia(id: Int) = {
    try {
      val eliminado = familiaRepo.eliminarFamilia(id)
      if (eliminado) {
        respond(Json.obj("mensaje" -> "Familia eliminada exitosamente"))
      } else {
        respond(Json.obj("error" -> "No se encontró la familia"), 404)
      }
    } catch {
      case e: org.postgresql.util.PSQLException if e.getSQLState == "23503" =>
        respond(Json.obj("error" -> "No se puede eliminar la familia porque tiene miembros asociados"), 409)
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  @cask.get("/api/familias/:id/beneficiarios")
  def listarMiembrosFamilia(id: Int) = {
    try {
      val miembros = familiaRepo.obtenerMiembrosFamilia(id)
      respond(Json.toJson(miembros))
    } catch {
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  @cask.post("/api/familias/:id/beneficiarios")
  def agregarMiembroFamilia(id: Int, request: cask.Request) = {
    try {
      val body = Json.parse(request.text()).as[AgregarMiembroRequest]
      familiaRepo.agregarMiembro(id, body.personaId)
      respond(Json.obj("mensaje" -> s"Beneficiario ${body.personaId} agregado a familia $id"))
    } catch {
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  @cask.delete("/api/familias/:id/beneficiarios/:personaId")
  def quitarMiembroFamilia(id: Int, personaId: Int) = {
    try {
      val eliminado = familiaRepo.quitarMiembro(id, personaId)
      if (eliminado) {
        respond(Json.obj("mensaje" -> s"Beneficiario $personaId removido de familia $id"))
      } else {
        respond(Json.obj("error" -> "No se encontró el miembro en la familia"), 404)
      }
    } catch {
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  initialize()
}
