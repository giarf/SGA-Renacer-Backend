package cl.familiarenacer.sga.api.routes

import cl.familiarenacer.sga.api.ApiSupport
import cl.familiarenacer.sga.modelos._
import cl.familiarenacer.sga.repositorios.FamiliaRepository
import play.api.libs.json._

class FamiliasRoutes(familiaRepo: FamiliaRepository)(implicit cc: castor.Context, log: cask.Logger) extends cask.Routes with ApiSupport {

  // Endpoint handlers for familias CRUD will be implemented when the frontend consumes them.
  // Repository methods are already available: crearFamilia, obtenerFamilia, listarFamilias, etc.

  initialize()
}
