package cl.familiarenacer.sga.api

import cl.familiarenacer.sga.api.routes._
import cl.familiarenacer.sga.repositorios._

/**
 * Servidor Web API REST para SGA Renacer.
 * Delega todas las rutas a módulos especializados bajo api/routes/.
 */
object SgaApiApp extends cask.Main {

  override def host = "0.0.0.0"
  override def port = 8080

  // Inicialización de Repositorios
  val entidadRepo = new EntidadRepository(DB.ctx)
  val donacionRepo = new DonacionRepository(DB.ctx)
  val inventarioRepo = new InventarioRepository(DB.ctx)
  val institucionRepo = new InstitucionRepository(DB.ctx)
  val rolesRepo = new RolesRepository(DB.ctx)
  val familiaRepo = new FamiliaRepository(DB.ctx)
  val egresoRepo = new EgresoRepository(DB.ctx)
  val solicitudRepo = new SolicitudRepository(DB.ctx)
  val cuentaRepo = new CuentaFinancieraRepository(DB.ctx)

  // Composición de Rutas
  val allRoutes = Seq(
    new PersonasRoutes(entidadRepo),
    new EntidadesRoutes(entidadRepo),
    new InstitucionesRoutes(institucionRepo, entidadRepo),
    new RolesRoutes(rolesRepo),
    new FamiliasRoutes(familiaRepo),
    new IngresosRoutes(donacionRepo, inventarioRepo),
    new EgresosRoutes(egresoRepo),
    new CatalogoRoutes(inventarioRepo),
    new SolicitudesRoutes(solicitudRepo),
    new CuentasRoutes(cuentaRepo)
  )
}
