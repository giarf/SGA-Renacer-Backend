package cl.familiarenacer.sga.modelos

/**
 * Representa una operación de trueque o intercambio de recursos.
 * Vincula un Ingreso (lo que entra) con un Egreso (lo que sale) simultáneamente.
 *
 * @param ingresoId Identificador del ingreso, referencia a IngresoRecurso.id.
 * @param egresoId Identificador del egreso generado automáticamente, referencia a EgresoRecurso.id.
 * @param entidadIntercambioId ID de la entidad con la que se realiza el trueque.
 * @param valorAcordadoPermuta Valor monetario asignado al intercambio.
 * @param observaciones Detalles u observaciones del acuerdo.
 */
case class TruequeRecurso(
  ingresoId: Int,
  egresoId: Option[Int],
  entidadIntercambioId: Option[Int],
  valorAcordadoPermuta: Option[BigDecimal],
  observaciones: Option[String]
)
