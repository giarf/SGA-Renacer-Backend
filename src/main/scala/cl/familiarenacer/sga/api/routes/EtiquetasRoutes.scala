package cl.familiarenacer.sga.api.routes

import cl.familiarenacer.sga.api.ApiSupport
import cl.familiarenacer.sga.modelos.Etiqueta
import cl.familiarenacer.sga.repositorios.EtiquetaRepository
import play.api.libs.json._

class EtiquetasRoutes(etiquetaRepo: EtiquetaRepository)(implicit cc: castor.Context, log: cask.Logger) extends cask.Routes with ApiSupport {
  case class EtiquetaRequest(nombre: String, descripcion: Option[String] = None, color: Option[String] = None, activa: Boolean = true)
  case class EntidadesMasivasRequest(entidadIds: List[Int])

  implicit val etiquetaRequestFormat: OFormat[EtiquetaRequest] = Json.using[Json.WithDefaultValues].format[EtiquetaRequest]
  implicit val entidadesMasivasFormat: OFormat[EntidadesMasivasRequest] = Json.format[EntidadesMasivasRequest]

  private def slugify(value: String): String = {
    java.text.Normalizer.normalize(value.trim.toLowerCase, java.text.Normalizer.Form.NFD)
      .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
      .replaceAll("[^a-z0-9]+", "-")
      .replaceAll("(^-|-$)", "")
  }

  @cask.options("/api/etiquetas")
  def etiquetasOptions() = corsOptions()

  @cask.options("/api/etiquetas/:id")
  def etiquetaOptions(id: Int) = corsOptions()

  @cask.options("/api/entidades/:id/etiquetas")
  def entidadEtiquetasOptions(id: Int) = corsOptions()

  @cask.options("/api/entidades/:id/etiquetas/:etiquetaId")
  def entidadEtiquetaOptions(id: Int, etiquetaId: Int) = corsOptions()

  @cask.options("/api/etiquetas/:etiquetaId/entidades")
  def etiquetaEntidadesOptions(etiquetaId: Int) = corsOptions()

  @cask.get("/api/etiquetas")
  def listarEtiquetas() = {
    try respond(Json.toJson(etiquetaRepo.listarEtiquetas()))
    catch {
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  @cask.post("/api/etiquetas")
  def crearEtiqueta(request: cask.Request) = {
    try {
      val body = Json.parse(request.text()).as[EtiquetaRequest]
      val slug = slugify(body.nombre)
      if (slug.isEmpty) respond(Json.obj("error" -> "El nombre de la etiqueta es obligatorio"), 400)
      else {
        val id = etiquetaRepo.crearEtiqueta(Etiqueta(0, body.nombre.trim, slug, body.descripcion, body.color, body.activa))
        respond(Json.obj("id" -> id, "mensaje" -> "Etiqueta creada"), 201)
      }
    } catch {
      case e: org.postgresql.util.PSQLException if e.getSQLState == "23505" =>
        respond(Json.obj("error" -> "Ya existe una etiqueta con ese nombre"), 409)
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  @cask.put("/api/etiquetas/:id")
  def actualizarEtiqueta(id: Int, request: cask.Request) = {
    try {
      val body = Json.parse(request.text()).as[EtiquetaRequest]
      val rows = etiquetaRepo.actualizarEtiqueta(id, Etiqueta(id, body.nombre.trim, slugify(body.nombre), body.descripcion, body.color, body.activa))
      if (rows > 0) respond(Json.obj("mensaje" -> "Etiqueta actualizada"))
      else respond(Json.obj("error" -> "Etiqueta no encontrada"), 404)
    } catch {
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  @cask.get("/api/entidades/:id/etiquetas")
  def etiquetasEntidad(id: Int) = {
    try respond(Json.toJson(etiquetaRepo.etiquetasPorEntidad(id)))
    catch {
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  @cask.post("/api/entidades/:id/etiquetas/:etiquetaId")
  def asignarEtiqueta(id: Int, etiquetaId: Int) = {
    try {
      etiquetaRepo.asignarEtiqueta(id, etiquetaId)
      respond(Json.obj("mensaje" -> "Etiqueta asignada"))
    } catch {
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  @cask.delete("/api/entidades/:id/etiquetas/:etiquetaId")
  def quitarEtiqueta(id: Int, etiquetaId: Int) = {
    try {
      etiquetaRepo.quitarEtiqueta(id, etiquetaId)
      respond(Json.obj("mensaje" -> "Etiqueta quitada"))
    } catch {
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  @cask.post("/api/etiquetas/:etiquetaId/entidades")
  def asignarEtiquetaMasiva(etiquetaId: Int, request: cask.Request) = {
    try {
      val body = Json.parse(request.text()).as[EntidadesMasivasRequest]
      val asignadas = etiquetaRepo.asignarEtiquetaMasiva(etiquetaId, body.entidadIds)
      respond(Json.obj("mensaje" -> "Etiqueta asignada a entidades", "asignadas" -> asignadas))
    } catch {
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  @cask.delete("/api/etiquetas/:etiquetaId/entidades")
  def quitarEtiquetaMasiva(etiquetaId: Int, request: cask.Request) = {
    try {
      val body = Json.parse(request.text()).as[EntidadesMasivasRequest]
      val quitadas = etiquetaRepo.quitarEtiquetaMasiva(etiquetaId, body.entidadIds)
      respond(Json.obj("mensaje" -> "Etiqueta quitada de entidades", "quitadas" -> quitadas))
    } catch {
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  initialize()
}
