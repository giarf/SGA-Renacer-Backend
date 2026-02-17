package cl.familiarenacer.sga.repositorios

import cl.familiarenacer.sga.modelos.{SolicitudMaterial, ItemSolicitud}
import io.getquill._

/**
 * Repositorio para manejar la persistencia de Solicitudes de Material.
 *
 * @param ctx Contexto de Quill inyectado (generalmente DB.ctx).
 */
class SolicitudRepository(val ctx: PostgresJdbcContext[SnakeCase.type]) {
  import ctx._

  /**
   * Lista todas las solicitudes.
   */
  def listarSolicitudes(): List[SolicitudMaterial] = {
    ctx.run(query[SolicitudMaterial])
  }

  /**
   * Crea una nueva solicitud con sus ítems de forma transaccional.
   * @return El ID de la solicitud creada.
   */
  def crearSolicitud(solicitud: SolicitudMaterial, items: List[ItemSolicitud]): Long = {
    ctx.transaction {
      // 1. Insertar la solicitud
      val solicitudId = ctx.run(
        query[SolicitudMaterial]
          .insertValue(lift(solicitud))
          .returningGenerated(_.id)
      )

      // 2. Insertar los ítems asociados
      if (items.nonEmpty) {
        val itemsConId = items.map(_.copy(solicitudId = Some(solicitudId)))
        ctx.run(
          liftQuery(itemsConId).foreach(item => query[ItemSolicitud].insertValue(item))
        )
      }

      solicitudId.toLong
    }
  }

  /**
   * Obtiene una solicitud con todos sus ítems.
   */
  def obtenerSolicitud(id: Int): Option[(SolicitudMaterial, List[ItemSolicitud])] = {
    val solicitud = ctx.run(
      query[SolicitudMaterial].filter(_.id == lift(id))
    ).headOption

    solicitud.map { s =>
      val items = ctx.run(
        query[ItemSolicitud].filter(_.solicitudId.contains(lift(id)))
      )
      (s, items)
    }
  }

  /**
   * Actualiza el estado de una solicitud (y opcionalmente el autorizador).
   */
  def actualizarEstadoSolicitud(id: Int, estado: String, autorizadorId: Option[Int]): Long = {
    ctx.run(
      query[SolicitudMaterial]
        .filter(_.id == lift(id))
        .update(
          _.estado -> lift(Option(estado)),
          _.autorizadorId -> lift(autorizadorId)
        )
    )
  }
}
