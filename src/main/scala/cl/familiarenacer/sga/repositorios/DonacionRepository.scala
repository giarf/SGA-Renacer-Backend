package cl.familiarenacer.sga.repositorios

import cl.familiarenacer.sga.modelos.{IngresoDonacion, IngresoPecuniario, IngresoRecurso}
import io.getquill._

/**
 * Repositorio especializado en la gestión de Donaciones.
 * Maneja transacciones complejas que involucran múltiples tablas.
 *
 * @param ctx Contexto de Quill inyectado.
 */
class DonacionRepository(val ctx: PostgresJdbcContext[SnakeCase.type]) {
  import ctx._

  /**
   * Registra una Donación de Dinero de manera atómica.
   * Esta operación impacta tres tablas:
   * 1. ingreso_recurso (Cabecera general del ingreso).
   * 2. ingreso_donacion (Datos del certificado y propósito).
   * 3. ingreso_pecuniario (Datos del movimiento de dinero a una cuenta).
   *
   * @param ingreso Datos generales del ingreso (fecha, monto, origen).
   * @param donacion Datos específicos de la donación (certificado).
   * @param pecuniario Datos financieros (cuenta destino).
   * @return El ID del ingreso generado.
   */
  def registrarDonacionDinero(ingreso: IngresoRecurso, donacion: IngresoDonacion, pecuniario: IngresoPecuniario): Long = {
    ctx.transaction {
      // 1. Insertamos la cabecera (IngresoRecurso) y obtenemos el ID.
      val ingresoId = ctx.run(
        query[IngresoRecurso]
          .insertValue(lift(ingreso))
          .returningGenerated(_.id)
      )

      // 2. Preparamos y registramos el detalle de donación con el ID generado.
      val donacionConId = donacion.copy(ingresoId = ingresoId)
      ctx.run(
        query[IngresoDonacion].insertValue(lift(donacionConId))
      )

      // 3. Preparamos y registramos el detalle pecuniario con el ID generado.
      val pecuniarioConId = pecuniario.copy(ingresoId = ingresoId)
      ctx.run(
        query[IngresoPecuniario].insertValue(lift(pecuniarioConId))
      )

      // Retornamos el ID principal de la transacción.
      ingresoId.toLong
    }
  }
}
