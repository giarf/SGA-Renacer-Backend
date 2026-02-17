package cl.familiarenacer.sga.repositorios

import cl.familiarenacer.sga.modelos.{IngresoDonacion, IngresoPecuniario, IngresoRecurso, IngresoCompra, IngresoSubvencion, DetalleIngresoRecurso}
import io.getquill._

/**
 * Repositorio especializado en la gestión de Ingresos (Donaciones, Compras, Subvenciones, Pecuniarios).
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

  /**
   * Registra una Compra de manera atómica.
   * Esta operación impacta:
   * 1. ingreso_recurso (Cabecera general del ingreso).
   * 2. ingreso_compra (Datos de la factura y cuenta origen).
   * 3. detalle_ingreso_recurso (Detalles de los ítems comprados).
   *
   * @return El ID del ingreso generado.
   */
  def registrarCompra(ingreso: IngresoRecurso, compra: IngresoCompra, detalles: List[DetalleIngresoRecurso]): Long = {
    ctx.transaction {
      // 1. Insertar el ingreso base
      val ingresoId = ctx.run(
        query[IngresoRecurso]
          .insertValue(lift(ingreso))
          .returningGenerated(_.id)
      )

      // 2. Insertar el detalle de compra
      val compraConId = compra.copy(ingresoId = ingresoId)
      ctx.run(
        query[IngresoCompra].insertValue(lift(compraConId))
      )

      // 3. Insertar los detalles de los ítems
      if (detalles.nonEmpty) {
        val detallesConId = detalles.map(_.copy(ingresoId = Some(ingresoId)))
        ctx.run(
          liftQuery(detallesConId).foreach(d => query[DetalleIngresoRecurso].insertValue(d))
        )
      }

      ingresoId.toLong
    }
  }

  /**
   * Registra un Ingreso Pecuniario standalone (sin ser donación ni compra).
   * Útil para registrar transferencias, depósitos, etc.
   *
   * @return El ID del ingreso generado.
   */
  def registrarPecuniario(ingreso: IngresoRecurso, pecuniario: IngresoPecuniario): Long = {
    ctx.transaction {
      val ingresoId = ctx.run(
        query[IngresoRecurso]
          .insertValue(lift(ingreso))
          .returningGenerated(_.id)
      )

      val pecuniarioConId = pecuniario.copy(ingresoId = ingresoId)
      ctx.run(
        query[IngresoPecuniario].insertValue(lift(pecuniarioConId))
      )

      ingresoId.toLong
    }
  }

  /**
   * Registra una Subvención de manera atómica.
   *
   * @return El ID del ingreso generado.
   */
  def registrarSubvencion(ingreso: IngresoRecurso, subvencion: IngresoSubvencion, pecuniario: Option[IngresoPecuniario]): Long = {
    ctx.transaction {
      val ingresoId = ctx.run(
        query[IngresoRecurso]
          .insertValue(lift(ingreso))
          .returningGenerated(_.id)
      )

      val subvencionConId = subvencion.copy(ingresoId = ingresoId)
      ctx.run(
        query[IngresoSubvencion].insertValue(lift(subvencionConId))
      )

      // Opcionalmente registrar el pecuniario si viene
      pecuniario.foreach { pec =>
        val pecConId = pec.copy(ingresoId = ingresoId)
        ctx.run(
          query[IngresoPecuniario].insertValue(lift(pecConId))
        )
      }

      ingresoId.toLong
    }
  }

  /**
   * Lista todos los ingresos.
   */
  def listarIngresos(): List[IngresoRecurso] = {
    ctx.run(query[IngresoRecurso])
  }

  /**
   * Obtiene un ingreso por su ID.
   */
  def obtenerIngreso(id: Int): Option[IngresoRecurso] = {
    ctx.run(
      query[IngresoRecurso].filter(_.id == lift(id))
    ).headOption
  }

  /**
   * Elimina un ingreso.
   * NOTA: Esto debería eliminar en cascada los detalles asociados si están configurados en la DB.
   */
  def eliminarIngreso(id: Int): Boolean = {
    ctx.run(
      query[IngresoRecurso].filter(_.id == lift(id)).delete
    ) > 0
  }
}
