package cl.familiarenacer.sga.api.routes

import cl.familiarenacer.sga.api.ApiSupport
import cl.familiarenacer.sga.modelos._
import cl.familiarenacer.sga.repositorios.EgresoRepository
import play.api.libs.json._

class EgresosRoutes(egresoRepo: EgresoRepository)(implicit cc: castor.Context, log: cask.Logger) extends cask.Routes with ApiSupport {

  // ===== DTOs =====

  case class RegistrarAyudaSocialRequest(
    egreso: EgresoRecurso,
    ayuda: EgresoAyudaSocial,
    detalles: List[DetalleEgresoRecurso]
  )
  implicit val registrarAyudaSocialFormat: OFormat[RegistrarAyudaSocialRequest] = Json.format[RegistrarAyudaSocialRequest]

  case class RegistrarConsumoInternoRequest(
    egreso: EgresoRecurso,
    consumo: EgresoConsumoInterno,
    detalles: List[DetalleEgresoRecurso]
  )
  implicit val registrarConsumoInternoFormat: OFormat[RegistrarConsumoInternoRequest] = Json.format[RegistrarConsumoInternoRequest]

  // Endpoint handlers for egresos CRUD will be implemented when the frontend consumes them.
  // Repository methods are already available: registrarAyudaSocial, registrarConsumoInterno, etc.

  initialize()
}
