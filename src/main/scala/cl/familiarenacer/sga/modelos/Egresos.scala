package cl.familiarenacer.sga.modelos

import java.time.LocalDate

/**
 * Representa una salida o egreso de recursos.
 *
 * @param id Identificador único del egreso.
 * @param fecha Fecha en que se materializó el egreso.
 * @param tipoEgreso Tipo de egreso (ayuda social, consumo interno, etc.).
 * @param montoValorizadoTotal Valor monetario total de lo egresado.
 * @param creadoPorId ID del trabajador que registró el egreso.
 */
case class EgresoRecurso(
  id: Int,
  fecha: Option[LocalDate],
  tipoEgreso: Option[String],
  montoValorizadoTotal: Option[BigDecimal],
  creadoPorId: Option[Int]
)

/**
 * Detalle específico para egreso destinado a ayuda social directa.
 *
 * @param egresoId Identificador del egreso, referencia a EgresoRecurso.id.
 * @param beneficiarioPersonaId ID del beneficiario que recibe la ayuda.
 * @param motivoEntrega Razón o justificación de la entrega.
 */
case class EgresoAyudaSocial(
  egresoId: Int,
  beneficiarioPersonaId: Option[Int],
  motivoEntrega: Option[String]
)

/**
 * Detalle específico para egreso por consumo interno de la fundación.
 *
 * @param egresoId Identificador del egreso.
 * @param programaEvento Programa o evento para el cual se consumieron los recursos.
 * @param responsablePersonaId ID de la persona responsable del retiro/uso.
 */
case class EgresoConsumoInterno(
  egresoId: Int,
  programaEvento: Option[String],
  responsablePersonaId: Option[Int]
)

/**
 * Detalle de los ítems entregados o consumidos.
 *
 * @param id Identificador único del detalle.
 * @param egresoId ID del egreso asociado.
 * @param itemCatalogoId ID del ítem egresado.
 * @param cantidad Cantidad egresada.
 * @param precioUnitarioPpp Precio promedio ponderado al momento del egreso.
 */
case class DetalleEgresoRecurso(
  id: Int,
  egresoId: Option[Int],
  itemCatalogoId: Option[Int],
  cantidad: Option[BigDecimal],
  precioUnitarioPpp: Option[BigDecimal]
)
