package cl.familiarenacer.sga.modelos

import java.time.LocalDateTime

/**
 * Representa una solicitud de materiales o recursos.
 *
 * @param id Identificador único de la solicitud.
 * @param solicitanteId ID de la persona que realiza la solicitud.
 * @param programa Programa o proyecto asociado a la solicitud.
 * @param fechaSolicitud Fecha y hora de creación de la solicitud.
 * @param estado Estado actual (pendiente, aprobado, rechazado, entregado).
 * @param autorizadorId ID del directivo que autorizó la solicitud.
 */
case class SolicitudMaterial(
  id: Int,
  solicitanteId: Option[Int],
  programa: Option[String],
  fechaSolicitud: Option[LocalDateTime],
  estado: Option[String],
  autorizadorId: Option[Int]
)

/**
 * Detalle de un ítem dentro de una solicitud.
 *
 * @param id Identificador único del detalle.
 * @param solicitudId ID de la solicitud asociada.
 * @param itemCatalogoId ID del ítem solicitado del catálogo.
 * @param descripcionManual Descripción si el ítem no está en catálogo.
 * @param cantidadRequerida Cantidad solicitada.
 * @param cantidadEntregada Cantidad efectivamente entregada (si aplica).
 */
case class ItemSolicitud(
  id: Int,
  solicitudId: Option[Int],
  itemCatalogoId: Option[Int],
  descripcionManual: Option[String],
  cantidadRequerida: Option[BigDecimal],
  cantidadEntregada: Option[BigDecimal]
)
