package cl.familiarenacer.sga.repositorios

import cl.familiarenacer.sga.modelos.{IngresoDonacion, IngresoPecuniario, IngresoRecurso, IngresoCompra, IngresoSubvencion, DetalleIngresoRecurso, CuentaFinanciera, IngresoHistorial, ItemCatalogo, EgresoRecurso, EgresoPecuniario}
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
   * 2. ingreso_donacion (Datos de propósito del aporte).
   * 3. ingreso_pecuniario (Datos del movimiento de dinero a una cuenta).
   *
   * @param ingreso Datos generales del ingreso (fecha, monto, origen).
   * @param donacion Datos específicos de la donación (propósito).
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
      val donacionConId = donacion.copy(ingresoId = Some(ingresoId))
      ctx.run(
        query[IngresoDonacion].insertValue(lift(donacionConId))
      )

      // 3. Preparamos y registramos el detalle pecuniario con el ID generado.
      val pecuniarioConId = pecuniario.copy(ingresoId = Some(ingresoId))
      ctx.run(
        query[IngresoPecuniario].insertValue(lift(pecuniarioConId))
      )

      // 4. Actualizar saldo de la cuenta financiera
      val monto = ingreso.montoTotal.getOrElse(BigDecimal(0))
      val cero = BigDecimal(0)
      pecuniario.cuentaDestinoId.foreach { cuentaId =>
        ctx.run(
          query[CuentaFinanciera]
            .filter(_.id == lift(cuentaId))
            .update(c => c.saldoActual -> Option(c.saldoActual.getOrElse(lift(cero)) + lift(monto)))
        )
      }

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
      val compraConId = compra.copy(ingresoId = Some(ingresoId))
      ctx.run(
        query[IngresoCompra].insertValue(lift(compraConId))
      )

      // 3. Insertar los detalles de los ítems
      if (detalles.nonEmpty) {
        detalles.foreach { detalle =>
          val itemId = detalle.itemCatalogoId.filter(_ > 0)
            .getOrElse(throw new IllegalArgumentException("Cada detalle de compra debe incluir itemCatalogoId válido."))
          val cantidad = detalle.cantidad.getOrElse(BigDecimal(0))
          val precio = detalle.precioUnitarioIngreso.getOrElse(BigDecimal(0))

          if (cantidad <= 0) throw new IllegalArgumentException(s"Cantidad inválida para item $itemId.")
          if (precio < 0) throw new IllegalArgumentException(s"Precio unitario inválido para item $itemId.")

          // Lock pesimista para evitar condiciones de carrera en stock/PPP.
          val itemActual = ctx.run(
            query[ItemCatalogo].filter(_.id == lift(itemId)).forUpdate
          ).headOption.getOrElse(throw new IllegalArgumentException(s"Item con ID $itemId no existe"))

          val stockActual = itemActual.stockActual.getOrElse(BigDecimal(0))
          val valorTotalActual = itemActual.valorTotalStock.getOrElse(BigDecimal(0))
          val nuevoStock = stockActual + cantidad
          val nuevoValorTotal = valorTotalActual + (cantidad * precio)
          val nuevoPPP =
            if (nuevoStock > 0) (nuevoValorTotal / nuevoStock).setScale(4, BigDecimal.RoundingMode.HALF_UP)
            else BigDecimal(0)

          ctx.run(
            query[ItemCatalogo]
              .filter(_.id == lift(itemId))
              .update(
                _.stockActual -> lift(Option(nuevoStock)),
                _.valorTotalStock -> lift(Option(nuevoValorTotal)),
                _.precioPromedioPonderado -> lift(Option(nuevoPPP))
              )
          )

          ctx.run(
            query[DetalleIngresoRecurso].insert(
              _.ingresoId             -> lift(Option(ingresoId)),
              _.itemCatalogoId        -> lift(Option(itemId)),
              _.cantidad              -> lift(detalle.cantidad),
              _.precioUnitarioIngreso -> lift(detalle.precioUnitarioIngreso)
            )
          )
        }
      }

      // 4. Registrar egreso pecuniario espejo de la compra y descontar saldo.
      registrarEgresoPecuniarioPorCompra(ingresoId, ingreso, compra)

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

      val pecuniarioConId = pecuniario.copy(ingresoId = Some(ingresoId))
      ctx.run(
        query[IngresoPecuniario].insertValue(lift(pecuniarioConId))
      )

      // Actualizar saldo de la cuenta
      val monto = ingreso.montoTotal.getOrElse(BigDecimal(0))
      val cero = BigDecimal(0)
      pecuniario.cuentaDestinoId.foreach { cuentaId =>
        ctx.run(
          query[CuentaFinanciera]
            .filter(_.id == lift(cuentaId))
            .update(c => c.saldoActual -> Option(c.saldoActual.getOrElse(lift(cero)) + lift(monto)))
        )
      }

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

      val subvencionConId = subvencion.copy(ingresoId = Some(ingresoId))
      ctx.run(
        query[IngresoSubvencion].insertValue(lift(subvencionConId))
      )

      // Opcionalmente registrar el pecuniario si viene
      pecuniario.foreach { pec =>
        val pecConId = pec.copy(ingresoId = Some(ingresoId))
        ctx.run(
          query[IngresoPecuniario].insertValue(lift(pecConId))
        )

        // Actualizar saldo de la cuenta
        val monto = ingreso.montoTotal.getOrElse(BigDecimal(0))
        val cero = BigDecimal(0)
        pec.cuentaDestinoId.foreach { cuentaId =>
          ctx.run(
            query[CuentaFinanciera]
              .filter(_.id == lift(cuentaId))
              .update(c => c.saldoActual -> Option(c.saldoActual.getOrElse(lift(cero)) + lift(monto)))
          )
        }
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
   * Lista los ingresos con un tipo semántico para historial.
   */
  def listarIngresosConTipo(): List[IngresoHistorial] = {
    val ingresos = ctx.run(query[IngresoRecurso])
    val compras = ctx.run(query[IngresoCompra]).flatMap(_.ingresoId).toSet
    val subvenciones = ctx.run(query[IngresoSubvencion]).flatMap(_.ingresoId).toSet
    val donaciones = ctx.run(query[IngresoDonacion]).flatMap(_.ingresoId).toSet
    val pecuniarios = ctx.run(query[IngresoPecuniario]).flatMap(_.ingresoId).toSet
    val detallesMap = ctx.run(query[DetalleIngresoRecurso]).flatMap(_.ingresoId).groupBy(identity).view.mapValues(_.size).toMap

    val descripcionCompra = ctx.run(query[IngresoCompra]).flatMap { compra =>
      compra.ingresoId.map(_ -> compra.numeroFacturaBoleta.orElse(compra.montoNeto.map(n => s"Factura ${compra.numeroFacturaBoleta.getOrElse("")} - Neto $n")))
    }.collect { case (id, Some(desc)) => id -> desc }.toMap

    val descripcionDonacion = ctx.run(query[IngresoDonacion]).flatMap { donacion =>
      donacion.ingresoId.map(_ -> donacion.propositoEspecifico)
    }.collect { case (id, Some(desc)) => id -> desc }.toMap

    val descripcionSubvencion = ctx.run(query[IngresoSubvencion]).flatMap { sub =>
      sub.ingresoId.map(_ -> sub.nombreProyecto)
    }.collect { case (id, Some(desc)) => id -> desc }.toMap

    ingresos.map { ingreso =>
      val id = ingreso.id
      val tipo =
        if (compras.contains(id)) "Compra"
        else if (subvenciones.contains(id)) "Subvencion"
        else if (donaciones.contains(id) && detallesMap.getOrElse(id, 0) > 0) "DonacionBienes"
        else if (donaciones.contains(id)) "DonacionPecuniaria"
        else if (pecuniarios.contains(id)) "IngresoPecuniario"
        else ingreso.tipoTransaccion.getOrElse("Ingreso")

      val descripcion =
        if (compras.contains(id)) descripcionCompra.get(id)
        else if (subvenciones.contains(id)) descripcionSubvencion.get(id)
        else if (donaciones.contains(id)) descripcionDonacion.get(id)
        else None

      IngresoHistorial(
        id = ingreso.id,
        fecha = ingreso.fecha,
        tipo = tipo,
        montoTotal = ingreso.montoTotal,
        estado = ingreso.estado,
        descripcion = descripcion
      )
    }.sortBy(_.fecha)(Ordering.Option(Ordering[java.time.LocalDate]).reverse)
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

  private def registrarEgresoPecuniarioPorCompra(
    ingresoId: Int,
    ingreso: IngresoRecurso,
    compra: IngresoCompra
  ): Unit = {
    val cuentaId = compra.cuentaOrigenId.getOrElse(
      throw new IllegalArgumentException("cuentaOrigenId es obligatorio para registrar compra como egreso pecuniario.")
    )
    val monto = ingreso.montoTotal.getOrElse(BigDecimal(0))
    if (monto <= 0) {
      throw new IllegalArgumentException("montoTotal debe ser mayor a 0 para registrar compra como egreso pecuniario.")
    }

    val numeroDocumento = compra.numeroFacturaBoleta.map(_.trim).filter(_.nonEmpty).getOrElse("sin_documento")
    val egresoCompra = EgresoRecurso(
      fecha = ingreso.fecha,
      tipoEgreso = Some("Ajuste"),
      montoTotal = Some(monto),
      responsableInternoId = ingreso.responsableInternoId,
      anotaciones = Some(s"Egreso automático por compra ingreso_id=$ingresoId, doc=$numeroDocumento"),
      destinoEntidadId = ingreso.origenEntidadId,
      propositoEspecifico = Some(s"Compra asociada al ingreso $ingresoId")
    )

    val egresoId = ctx.run(
      query[EgresoRecurso]
        .insertValue(lift(egresoCompra))
        .returningGenerated(_.id)
    )

    ctx.run(
      query[EgresoPecuniario].insertValue(
        lift(
          EgresoPecuniario(
            egresoId = egresoId,
            cuentaOrigenId = Some(cuentaId),
            metodoTransferencia = Some("Compra")
          )
        )
      )
    )

    val cero = BigDecimal(0)
    val filas = ctx.run(
      query[CuentaFinanciera]
        .filter(_.id == lift(cuentaId))
        .update(c => c.saldoActual -> Option(c.saldoActual.getOrElse(lift(cero)) - lift(monto)))
    )

    if (filas == 0) {
      throw new IllegalArgumentException(s"Cuenta financiera con ID $cuentaId no existe")
    }
  }
}
