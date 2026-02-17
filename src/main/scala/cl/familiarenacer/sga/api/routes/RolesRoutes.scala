package cl.familiarenacer.sga.api.routes

import cl.familiarenacer.sga.api.ApiSupport
import cl.familiarenacer.sga.modelos._
import cl.familiarenacer.sga.repositorios.RolesRepository
import play.api.libs.json._

class RolesRoutes(rolesRepo: RolesRepository)(implicit cc: castor.Context, log: cask.Logger) extends cask.Routes with ApiSupport {

  // ===== LISTADOS INDEPENDIENTES =====

  @cask.get("/api/beneficiarios")
  def listarBeneficiarios() = {
    try {
      val resultado = rolesRepo.listarBeneficiarios().map { case (entidad, persona, beneficiario) =>
        Json.obj(
          "id" -> entidad.id, "rut" -> entidad.rut,
          "nombres" -> persona.nombres, "apellidos" -> persona.apellidos,
          "genero" -> persona.genero, "telefono" -> entidad.telefono,
          "correo" -> entidad.correo, "direccion" -> entidad.direccion,
          "comuna" -> entidad.comuna,
          "familiaId" -> beneficiario.familiaId,
          "escolaridad" -> beneficiario.escolaridad,
          "tallaRopa" -> beneficiario.tallaRopa,
          "observacionesMedicas" -> beneficiario.observacionesMedicas
        )
      }
      respond(Json.toJson(resultado))
    } catch {
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  @cask.get("/api/colaboradores")
  def listarColaboradores() = {
    try {
      val resultado = rolesRepo.listarColaboradores().map { case (entidad, persona, colaborador) =>
        Json.obj(
          "id" -> entidad.id, "rut" -> entidad.rut,
          "nombres" -> persona.nombres, "apellidos" -> persona.apellidos,
          "genero" -> persona.genero, "telefono" -> entidad.telefono,
          "correo" -> entidad.correo, "direccion" -> entidad.direccion,
          "comuna" -> entidad.comuna,
          "tipoColaborador" -> colaborador.tipoColaborador,
          "esAnonimo" -> colaborador.esAnonimo
        )
      }
      respond(Json.toJson(resultado))
    } catch {
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  @cask.get("/api/trabajadores")
  def listarTrabajadores() = {
    try {
      val resultado = rolesRepo.listarTrabajadores().map { case (entidad, persona, trabajador) =>
        Json.obj(
          "id" -> entidad.id, "rut" -> entidad.rut,
          "nombres" -> persona.nombres, "apellidos" -> persona.apellidos,
          "genero" -> persona.genero, "telefono" -> entidad.telefono,
          "correo" -> entidad.correo, "direccion" -> entidad.direccion,
          "comuna" -> entidad.comuna,
          "cargo" -> trabajador.cargo,
          "fechaIngreso" -> trabajador.fechaIngreso
        )
      }
      respond(Json.toJson(resultado))
    } catch {
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  @cask.get("/api/directivos")
  def listarDirectivos() = {
    try {
      val resultado = rolesRepo.listarDirectivos().map { case (entidad, persona, directivo) =>
        Json.obj(
          "id" -> entidad.id, "rut" -> entidad.rut,
          "nombres" -> persona.nombres, "apellidos" -> persona.apellidos,
          "genero" -> persona.genero, "telefono" -> entidad.telefono,
          "correo" -> entidad.correo, "direccion" -> entidad.direccion,
          "comuna" -> entidad.comuna,
          "cargo" -> directivo.cargo,
          "firmaDigitalUrl" -> directivo.firmaDigitalUrl
        )
      }
      respond(Json.toJson(resultado))
    } catch {
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  initialize()
}
