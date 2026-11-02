package cl.familiarenacer.sga.repositorios

import cl.familiarenacer.sga.modelos.{DetalleEgresoRecurso, EgresoRecurso, ItemSolicitud, SolicitudMaterial}
import io.getquill._

import java.time.{LocalDate, LocalDateTime}

/** Fila mínima de egreso_recurso para saber qué entregas tiene cada solicitud. */
case class EgresoSolicitud(id: Int, solicitudId: Option[Int], estado: Option[String])

/** Solicitud con sus ítems y los egresos (entregas) vigentes que la cerraron. */
case class SolicitudDetalle(solicitud: SolicitudMaterial, items: List[ItemSolicitud], egresoIds: List[Int])

/**
 * Solicitudes de programas: se piden ítems y se entregan en una sola vez.
 * Entregar genera un egreso "Consumo Interno" ligado a la solicitud; anular ese egreso
 * devuelve la solicitud a "Pendiente" (ver EgresoRepository.anularEgreso).
 *
 * @param ctx Contexto de Quill inyectado (generalmente DB.ctx).
 */
class SolicitudRepository(val ctx: PostgresJdbcContext[SnakeCase.type], egresoRepo: EgresoRepository) {
  import ctx._

  /** Lista las solicitudes, más recientes primero, con ítems y entregas vigentes. */
  def listarSolicitudes(): List[SolicitudDetalle] = {
    val solicitudes = ctx.run(query[SolicitudMaterial])
    val items = ctx.run(query[ItemSolicitud]).groupBy(_.solicitudId)
    val entregas = egresosPorSolicitud()
    solicitudes
      .sortBy(s => (s.fechaSolicitud.getOrElse(LocalDateTime.MIN), s.id))(Ordering[(LocalDateTime, Int)].reverse)
      .map(s => SolicitudDetalle(s, items.getOrElse(Some(s.id), Nil).sortBy(_.id), entregas.getOrElse(s.id, Nil)))
  }

  def obtenerSolicitud(id: Int): Option[SolicitudDetalle] =
    ctx.run(query[SolicitudMaterial].filter(_.id == lift(id))).headOption.map { s =>
      val items = ctx.run(query[ItemSolicitud].filter(_.solicitudId.contains(lift(id)))).sortBy(_.id)
      SolicitudDetalle(s, items, egresosPorSolicitud().getOrElse(id, Nil))
    }

  /** Crea una solicitud pendiente. Cada ítem es del catálogo o una descripción libre. */
  def crearSolicitud(solicitud: SolicitudMaterial, items: List[ItemSolicitud]): Long = {
    if (solicitud.solicitanteId.forall(_ <= 0)) throw new IllegalArgumentException("Selecciona a la persona solicitante")
    if (solicitud.programa.forall(_.trim.isEmpty)) throw new IllegalArgumentException("Indica el programa o actividad")
    if (items.isEmpty) throw new IllegalArgumentException("Agrega al menos un ítem")
    items.foreach { item =>
      val tieneItem = item.itemCatalogoId.exists(_ > 0) || item.descripcionManual.exists(_.trim.nonEmpty)
      if (!tieneItem) throw new IllegalArgumentException("Cada ítem necesita un ítem del catálogo o una descripción")
      if (item.cantidadRequerida.forall(_ <= 0)) throw new IllegalArgumentException("Cada ítem necesita una cantidad mayor a cero")
    }

    ctx.transaction {
      val nueva = solicitud.copy(
        id = 0,
        programa = solicitud.programa.map(_.trim),
        fechaSolicitud = solicitud.fechaSolicitud.orElse(Some(LocalDateTime.now())),
        estado = Some("Pendiente"),
        autorizadorId = None
      )
      val solicitudId = ctx.run(query[SolicitudMaterial].insertValue(lift(nueva)).returningGenerated(_.id))
      items.foreach { item =>
        ctx.run(
          query[ItemSolicitud].insert(
            _.solicitudId -> lift(Option(solicitudId)),
            _.itemCatalogoId -> lift(item.itemCatalogoId.filter(_ > 0)),
            _.descripcionManual -> lift(item.descripcionManual.map(_.trim).filter(_.nonEmpty)),
            _.cantidadRequerida -> lift(item.cantidadRequerida),
            _.cantidadEntregada -> lift(Option.empty[BigDecimal])
          )
        )
      }
      solicitudId.toLong
    }
  }

  /**
   * Entrega una solicitud pendiente de una vez. Los ítems entregados pueden diferir de lo pedido
   * (más, menos, agregados o quitados): lo que se descuenta es lo que salió de verdad.
   *
   * @return ID del egreso "Consumo Interno" generado.
   */
  def entregarSolicitud(
    id: Int,
    fecha: Option[LocalDate],
    responsableInternoId: Int,
    anotaciones: Option[String],
    entregados: List[(Int, BigDecimal)]
  ): Long = {
    if (responsableInternoId <= 0) throw new IllegalArgumentException("Selecciona quién entrega")
    if (entregados.isEmpty) throw new IllegalArgumentException("Agrega al menos un ítem a entregar")
    entregados.foreach { case (itemId, cantidad) =>
      if (itemId <= 0) throw new IllegalArgumentException("Cada ítem entregado debe ser del catálogo")
      if (cantidad <= 0) throw new IllegalArgumentException("Cada ítem entregado necesita una cantidad mayor a cero")
    }
    val porItem = entregados.groupMapReduce(_._1)(_._2)(_ + _)

    ctx.transaction {
      val solicitud = ctx.run(query[SolicitudMaterial].filter(_.id == lift(id)).forUpdate).headOption
        .getOrElse(throw new NoSuchElementException(s"Solicitud $id no encontrada"))
      if (!solicitud.estado.exists(_.equalsIgnoreCase("Pendiente"))) {
        throw new IllegalArgumentException(s"Solo se entregan solicitudes pendientes (esta está ${solicitud.estado.getOrElse("sin estado")})")
      }

      val egreso = EgresoRecurso(
        fecha = fecha.orElse(Some(LocalDate.now())),
        tipoEgreso = Some("Consumo Interno"),
        responsableInternoId = Some(responsableInternoId),
        anotaciones = anotaciones.map(_.trim).filter(_.nonEmpty),
        destinoEntidadId = solicitud.solicitanteId,
        propositoEspecifico = solicitud.programa,
        estado = Some("Cerrado")
      )
      val detalles = porItem.toList.sortBy(_._1).map { case (itemId, cantidad) =>
        DetalleEgresoRecurso(itemCatalogoId = Some(itemId), cantidad = Some(cantidad))
      }
      // Descuenta stock al PPP y falla si no alcanza (misma regla que cualquier egreso).
      val egresoId = egresoRepo.crearEgreso(egreso, None, detalles).toInt
      egresoRepo.vincularSolicitud(egresoId, id)

      // Lo entregado de cada línea pedida; lo agregado en la entrega queda solo en el egreso.
      val pedidos = ctx.run(query[ItemSolicitud].filter(_.solicitudId.contains(lift(id))))
      pedidos.foreach { pedido =>
        val entregado = pedido.itemCatalogoId.flatMap(porItem.get).getOrElse(BigDecimal(0))
        ctx.run(query[ItemSolicitud].filter(_.id == lift(pedido.id)).update(_.cantidadEntregada -> lift(Option(entregado))))
      }
      ctx.run(query[SolicitudMaterial].filter(_.id == lift(id)).update(_.estado -> lift(Option("Entregada"))))
      egresoId.toLong
    }
  }

  /** Cancela una solicitud pendiente (no se va a entregar). */
  def cancelarSolicitud(id: Int): Boolean = {
    val solicitud = ctx.run(query[SolicitudMaterial].filter(_.id == lift(id))).headOption
      .getOrElse(throw new NoSuchElementException(s"Solicitud $id no encontrada"))
    if (!solicitud.estado.exists(_.equalsIgnoreCase("Pendiente"))) {
      throw new IllegalArgumentException("Solo se cancelan solicitudes pendientes")
    }
    ctx.run(query[SolicitudMaterial].filter(_.id == lift(id)).update(_.estado -> lift(Option("Cancelada")))) > 0
  }

  /** IDs de egresos vigentes (no anulados) por solicitud. */
  private def egresosPorSolicitud(): Map[Int, List[Int]] =
    ctx.run(quote {
      infix"""SELECT id, solicitud_id, estado FROM egreso_recurso
        WHERE solicitud_id IS NOT NULL AND lower(coalesce(estado, '')) <> 'anulado'""".as[Query[EgresoSolicitud]]
    }).collect { case EgresoSolicitud(egresoId, Some(solicitudId), _) => solicitudId -> egresoId }
      .groupMap(_._1)(_._2)
      .view.mapValues(_.sorted).toMap
}
