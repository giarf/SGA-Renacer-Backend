package cl.familiarenacer.sga.repositorios

import cl.familiarenacer.sga.modelos.{CuentaFinanciera, DetalleEgresoRecurso, EgresoDetalle, EgresoPecuniario, EgresoRecurso, ItemCatalogo}
import io.getquill._

/**
 * Repositorio para manejar la persistencia de Egresos.
 * Soporta egresos con detalles de inventario y egresos pecuniarios.
 */
class EgresoRepository(val ctx: PostgresJdbcContext[SnakeCase.type]) {
  import ctx._

  def crearEgreso(
    egreso: EgresoRecurso,
    pecuniario: Option[EgresoPecuniario],
    detalles: List[DetalleEgresoRecurso]
  ): Long = {
    ctx.transaction {
      val detallesConPrecio = completarDetallesConPPP(detalles)
      val montoTotal = calcularMontoTotal(egreso, pecuniario, detallesConPrecio)

      val egresoId = ctx.run(
        query[EgresoRecurso].insert(
          _.fecha -> lift(egreso.fecha),
          _.tipoEgreso -> lift(egreso.tipoEgreso),
          _.montoTotal -> lift(Option(montoTotal)),
          _.responsableInternoId -> lift(egreso.responsableInternoId),
          _.anotaciones -> lift(egreso.anotaciones),
          _.destinoEntidadId -> lift(egreso.destinoEntidadId),
          _.propositoEspecifico -> lift(egreso.propositoEspecifico)
        ).returningGenerated(_.id)
      )

      pecuniario.foreach { pec =>
        val cuentaId = pec.cuentaOrigenId.getOrElse(
          throw new IllegalArgumentException("cuentaOrigenId es obligatorio para egreso pecuniario")
        )

        ctx.run(
          query[EgresoPecuniario].insertValue(lift(pec.copy(egresoId = egresoId)))
        )

        // Egreso pecuniario descuenta saldo.
        ajustarSaldoCuenta(cuentaId, -montoTotal)
      }

      if (detallesConPrecio.nonEmpty) {
        detallesConPrecio.foreach { detalle =>
          descontarInventarioPorDetalle(detalle)
          ctx.run(
            query[DetalleEgresoRecurso].insert(
              _.egresoId -> lift(Option(egresoId)),
              _.itemCatalogoId -> lift(detalle.itemCatalogoId),
              _.cantidad -> lift(detalle.cantidad),
              _.precioUnitarioPpp -> lift(detalle.precioUnitarioPpp)
            )
          )
        }
      }

      egresoId.toLong
    }
  }

  def listarEgresos(tipoEgreso: Option[String], destinoEntidadId: Option[Int]): List[EgresoRecurso] = {
    val resultado = (tipoEgreso, destinoEntidadId) match {
      case (Some(tipo), Some(destinoId)) =>
        ctx.run(
          query[EgresoRecurso]
            .filter(e => e.tipoEgreso == lift(Option(tipo)) && e.destinoEntidadId == lift(Option(destinoId)))
        )
      case (Some(tipo), None) =>
        ctx.run(
          query[EgresoRecurso]
            .filter(_.tipoEgreso == lift(Option(tipo)))
        )
      case (None, Some(destinoId)) =>
        ctx.run(
          query[EgresoRecurso]
            .filter(_.destinoEntidadId == lift(Option(destinoId)))
        )
      case (None, None) =>
        ctx.run(query[EgresoRecurso])
    }

    resultado.sortBy(_.fecha)(Ordering.Option(Ordering[java.time.LocalDate]).reverse)
  }

  def obtenerEgresoDetalle(id: Int): Option[EgresoDetalle] = {
    val egresoOpt = ctx.run(
      query[EgresoRecurso].filter(_.id == lift(id))
    ).headOption

    egresoOpt.map { egreso =>
      val pecuniario = ctx.run(
        query[EgresoPecuniario].filter(_.egresoId == lift(id))
      ).headOption

      val detalles = ctx.run(
        query[DetalleEgresoRecurso].filter(_.egresoId == lift(Option(id)))
      )

      EgresoDetalle(
        egreso = egreso,
        pecuniario = pecuniario,
        detalles = detalles
      )
    }
  }

  def actualizarEgreso(
    id: Int,
    egreso: EgresoRecurso,
    pecuniario: Option[EgresoPecuniario]
  ): Long = {
    ctx.transaction {
      val actual = ctx.run(
        query[EgresoRecurso].filter(_.id == lift(id))
      ).headOption.getOrElse(
        throw new NoSuchElementException(s"Egreso con ID $id no encontrado")
      )

      val pecActual = ctx.run(
        query[EgresoPecuniario].filter(_.egresoId == lift(id))
      ).headOption

      val montoAnterior = actual.montoTotal.getOrElse(BigDecimal(0))
      val montoNuevo = egreso.montoTotal.orElse(actual.montoTotal).getOrElse(BigDecimal(0))

      if (pecuniario.isDefined && montoNuevo <= 0) {
        throw new IllegalArgumentException("montoTotal debe ser mayor a 0 para egreso pecuniario")
      }

      reconciliarPecuniarioEnActualizacion(id, pecActual, pecuniario, montoAnterior, montoNuevo)

      ctx.run(
        query[EgresoRecurso]
          .filter(_.id == lift(id))
          .update(
            _.fecha -> lift(egreso.fecha.orElse(actual.fecha)),
            _.tipoEgreso -> lift(egreso.tipoEgreso.orElse(actual.tipoEgreso)),
            _.montoTotal -> lift(Option(montoNuevo)),
            _.responsableInternoId -> lift(egreso.responsableInternoId.orElse(actual.responsableInternoId)),
            _.anotaciones -> lift(egreso.anotaciones.orElse(actual.anotaciones)),
            _.destinoEntidadId -> lift(egreso.destinoEntidadId.orElse(actual.destinoEntidadId)),
            _.propositoEspecifico -> lift(egreso.propositoEspecifico.orElse(actual.propositoEspecifico))
          )
      )
    }
  }

  def eliminarEgreso(id: Int): Boolean = {
    ctx.transaction {
      val egresoOpt = ctx.run(
        query[EgresoRecurso].filter(_.id == lift(id))
      ).headOption

      egresoOpt.exists { egreso =>
        val pecuniario = ctx.run(
          query[EgresoPecuniario].filter(_.egresoId == lift(id))
        ).headOption

        val detallesAsociados = ctx.run(
          query[DetalleEgresoRecurso].filter(_.egresoId == lift(Option(id)))
        )

        pecuniario.foreach { pec =>
          val monto = egreso.montoTotal.getOrElse(BigDecimal(0))
          pec.cuentaOrigenId.foreach { cuentaId =>
            // Al eliminar un egreso pecuniario se revierte el descuento.
            ajustarSaldoCuenta(cuentaId, monto)
          }
        }

        // Si se elimina el egreso, revertimos la salida de stock asociada.
        detallesAsociados.foreach(revertirInventarioPorDetalle)

        ctx.run(
          query[DetalleEgresoRecurso].filter(_.egresoId == lift(Option(id))).delete
        )

        ctx.run(
          query[EgresoPecuniario].filter(_.egresoId == lift(id)).delete
        )

        ctx.run(
          query[EgresoRecurso].filter(_.id == lift(id)).delete
        ) > 0
      }
    }
  }

  private def reconciliarPecuniarioEnActualizacion(
    egresoId: Int,
    pecActual: Option[EgresoPecuniario],
    pecNuevo: Option[EgresoPecuniario],
    montoAnterior: BigDecimal,
    montoNuevo: BigDecimal
  ): Unit = {
    (pecActual, pecNuevo) match {
      case (Some(actual), Some(nuevo)) =>
        val cuentaAnterior = actual.cuentaOrigenId.getOrElse(
          throw new IllegalArgumentException("El egreso pecuniario actual no tiene cuenta_origen_id")
        )
        val cuentaNueva = nuevo.cuentaOrigenId.getOrElse(
          throw new IllegalArgumentException("cuentaOrigenId es obligatorio para egreso pecuniario")
        )

        if (cuentaAnterior == cuentaNueva) {
          // Ajuste por diferencia de monto en misma cuenta.
          ajustarSaldoCuenta(cuentaNueva, montoAnterior - montoNuevo)
        } else {
          // Revertimos en cuenta anterior y descontamos en nueva cuenta.
          ajustarSaldoCuenta(cuentaAnterior, montoAnterior)
          ajustarSaldoCuenta(cuentaNueva, -montoNuevo)
        }

        ctx.run(
          query[EgresoPecuniario]
            .filter(_.egresoId == lift(egresoId))
            .update(
              _.cuentaOrigenId -> lift(Option(cuentaNueva)),
              _.metodoTransferencia -> lift(nuevo.metodoTransferencia)
            )
        )

      case (Some(actual), None) =>
        // Si no se envía sección pecuniaria en PUT, mantenemos la actual.
        // Solo reconciliamos saldo cuando cambia el monto total.
        val cuentaActual = actual.cuentaOrigenId.getOrElse(
          throw new IllegalArgumentException("El egreso pecuniario actual no tiene cuenta_origen_id")
        )
        if (montoAnterior != montoNuevo) {
          ajustarSaldoCuenta(cuentaActual, montoAnterior - montoNuevo)
        }

      case (None, Some(nuevo)) =>
        val cuentaNueva = nuevo.cuentaOrigenId.getOrElse(
          throw new IllegalArgumentException("cuentaOrigenId es obligatorio para egreso pecuniario")
        )
        ctx.run(
          query[EgresoPecuniario].insertValue(lift(nuevo.copy(egresoId = egresoId)))
        )
        ajustarSaldoCuenta(cuentaNueva, -montoNuevo)

      case (None, None) =>
      // No hay sección pecuniaria en estado actual ni nuevo.
    }
  }

  private def ajustarSaldoCuenta(cuentaId: Int, delta: BigDecimal): Unit = {
    val cero = BigDecimal(0)
    val filas = ctx.run(
      query[CuentaFinanciera]
        .filter(_.id == lift(cuentaId))
        .update(c => c.saldoActual -> Option(c.saldoActual.getOrElse(lift(cero)) + lift(delta)))
    )

    if (filas == 0) {
      throw new IllegalArgumentException(s"Cuenta financiera con ID $cuentaId no existe")
    }
  }

  private def descontarInventarioPorDetalle(detalle: DetalleEgresoRecurso): Unit = {
    val itemId = detalle.itemCatalogoId.getOrElse(
      throw new IllegalArgumentException("Detalle sin itemCatalogoId al descontar inventario")
    )
    val cantidad = detalle.cantidad.getOrElse(BigDecimal(0))
    val precio = detalle.precioUnitarioPpp.getOrElse(BigDecimal(0))

    val itemActual = ctx.run(
      query[ItemCatalogo].filter(_.id == lift(itemId)).forUpdate
    ).headOption.getOrElse(
      throw new IllegalArgumentException(s"Item con ID $itemId no existe")
    )

    val stockActual = itemActual.stockActual.getOrElse(BigDecimal(0))
    val valorTotalActual = itemActual.valorTotalStock.getOrElse(BigDecimal(0))

    if (cantidad > stockActual) {
      throw new IllegalArgumentException(
        s"Stock insuficiente para item $itemId. Stock actual: $stockActual, solicitado: $cantidad"
      )
    }

    val nuevoStock = stockActual - cantidad
    val valorSalida = cantidad * precio
    val nuevoValorBruto = valorTotalActual - valorSalida
    val nuevoValorTotal =
      if (nuevoValorBruto.abs <= BigDecimal("0.0001")) BigDecimal(0)
      else nuevoValorBruto

    if (nuevoValorTotal < 0) {
      throw new IllegalArgumentException(
        s"El valor total de stock quedaría negativo para item $itemId"
      )
    }

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
  }

  private def revertirInventarioPorDetalle(detalle: DetalleEgresoRecurso): Unit = {
    val itemId = detalle.itemCatalogoId.getOrElse(
      throw new IllegalArgumentException("Detalle sin itemCatalogoId al revertir inventario")
    )
    val cantidad = detalle.cantidad.getOrElse(BigDecimal(0))
    val precio = detalle.precioUnitarioPpp.getOrElse(BigDecimal(0))

    val itemActual = ctx.run(
      query[ItemCatalogo].filter(_.id == lift(itemId)).forUpdate
    ).headOption.getOrElse(
      throw new IllegalArgumentException(s"Item con ID $itemId no existe")
    )

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
  }

  private def calcularMontoTotal(
    egreso: EgresoRecurso,
    pecuniario: Option[EgresoPecuniario],
    detalles: List[DetalleEgresoRecurso]
  ): BigDecimal = {
    if (pecuniario.isDefined) {
      val monto = egreso.montoTotal.getOrElse(
        throw new IllegalArgumentException("montoTotal es obligatorio cuando existe egreso_pecuniario")
      )
      if (monto <= 0) throw new IllegalArgumentException("montoTotal debe ser mayor a 0")
      monto
    } else if (detalles.nonEmpty) {
      detalles.map { d =>
        d.cantidad.getOrElse(BigDecimal(0)) * d.precioUnitarioPpp.getOrElse(BigDecimal(0))
      }.sum
    } else {
      egreso.montoTotal.getOrElse(BigDecimal(0))
    }
  }

  private def completarDetallesConPPP(detalles: List[DetalleEgresoRecurso]): List[DetalleEgresoRecurso] = {
    detalles.map { detalle =>
      val itemId = detalle.itemCatalogoId.filter(_ > 0).getOrElse(
        throw new IllegalArgumentException("Cada detalle debe incluir itemCatalogoId válido")
      )
      val cantidad = detalle.cantidad.getOrElse(BigDecimal(0))
      if (cantidad <= 0) throw new IllegalArgumentException(s"Cantidad inválida para item $itemId")

      val precio = detalle.precioUnitarioPpp.orElse {
        ctx.run(
          query[ItemCatalogo]
            .filter(_.id == lift(itemId))
            .map(_.precioPromedioPonderado)
        ).headOption.flatten
      }.getOrElse(
        throw new IllegalArgumentException(s"Precio PPP no disponible para item $itemId")
      )

      if (precio < 0) throw new IllegalArgumentException(s"Precio PPP inválido para item $itemId")

      detalle.copy(
        itemCatalogoId = Some(itemId),
        cantidad = Some(cantidad),
        precioUnitarioPpp = Some(precio)
      )
    }
  }
}
