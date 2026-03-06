package cl.familiarenacer.sga.modelos

import java.time.LocalDate

/**
 * Representa la cabecera de un egreso.
 * Corresponde a la tabla egreso_recurso.
 */
case class EgresoRecurso(
  id: Int = 0,
  fecha: Option[LocalDate] = None,
  tipoEgreso: Option[String] = None,
  montoTotal: Option[BigDecimal] = None,
  responsableInternoId: Option[Int] = None,
  anotaciones: Option[String] = None,
  destinoEntidadId: Option[Int] = None,
  propositoEspecifico: Option[String] = None
)

/**
 * Complemento financiero opcional del egreso (egreso_pecuniario).
 */
case class EgresoPecuniario(
  egresoId: Int = 0,
  cuentaOrigenId: Option[Int] = None,
  metodoTransferencia: Option[String] = None
)

/**
 * Detalle de ítems egresados desde inventario.
 */
case class DetalleEgresoRecurso(
  id: Int = 0,
  egresoId: Option[Int] = None,
  itemCatalogoId: Option[Int] = None,
  cantidad: Option[BigDecimal] = None,
  precioUnitarioPpp: Option[BigDecimal] = None
)

/**
 * Respuesta enriquecida para detalle de egreso.
 */
case class EgresoDetalle(
  egreso: EgresoRecurso,
  pecuniario: Option[EgresoPecuniario],
  detalles: List[DetalleEgresoRecurso]
)
