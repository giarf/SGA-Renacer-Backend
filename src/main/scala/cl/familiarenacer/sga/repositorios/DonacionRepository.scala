package cl.familiarenacer.sga.repositorios

import cl.familiarenacer.sga.modelos.{IngresoDetalle, IngresoDonacion, IngresoPecuniario, IngresoRecurso, IngresoCompra, IngresoSubvencion, DetalleIngresoRecurso, DetalleEgresoRecurso, CuentaFinanciera, IngresoHistorial, ItemCatalogo, EgresoRecurso, EgresoPecuniario, DetalleDonacionInput, OrigenDatos}
import io.getquill._
import java.sql.DriverManager

/**
 * Repositorio especializado en la gestión de Ingresos (Donaciones, Compras, Subvenciones, Pecuniarios).
 * Maneja transacciones complejas que involucran múltiples tablas.
 *
 * @param ctx Contexto de Quill inyectado.
 */
class DonacionRepository(val ctx: PostgresJdbcContext[SnakeCase.type]) {
  import ctx._
  private lazy val inventario = new InventarioRepository(ctx)

  asegurarColumnasEgresos()

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
  /**
   * El inventario sube por la suma de las líneas y el ingreso registra el monto de la boleta:
   * si no coinciden, la diferencia queda como descuadre en el balance de logs. Se tolera menos
   * de $1 (cantidades fraccionadas por precios enteros).
   */
  private def validarMontoCompra(montoTotal: Option[BigDecimal], detalles: List[DetalleIngresoRecurso]): Unit =
    if (detalles.nonEmpty) {
      val sumaLineas = detalles.map(d => d.cantidad.getOrElse(BigDecimal(0)) * d.precioUnitarioIngreso.getOrElse(BigDecimal(0))).sum
      val monto = montoTotal.getOrElse(sumaLineas)
      if ((monto - sumaLineas).abs >= 1) {
        throw new IllegalArgumentException(
          s"El monto total de la compra ($$${monto.bigDecimal.toPlainString}) no coincide con la suma de los ítems ($$${sumaLineas.bigDecimal.toPlainString}). Corrige los precios o el neto/IVA."
        )
      }
    }

  def registrarCompra(ingreso: IngresoRecurso, compra: IngresoCompra, detalles: List[DetalleIngresoRecurso]): Long =
    registrarCompraConItems(ingreso, compra, detalles.map(d => DetalleDonacionInput(d, "", None, None)))

  /**
   * Registra una compra. Cada ítem se resuelve por itemCatalogoId, código de barras o nombre (y se crea si no existe).
   * Al final recalcula, en la misma transacción, el precio de referencia (mediana 180 días) de cada ítem comprado.
   */
  def registrarCompraConItems(ingreso: IngresoRecurso, compra: IngresoCompra, items: List[DetalleDonacionInput]): Long = {
    validarMontoCompra(ingreso.montoTotal, items.map(_.detalle))
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
      val itemsComprados = items.map { input =>
        val detalle = input.detalle
        val itemId = inventario.resolverItem(input, OrigenDatos.Compra)
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
        itemId
      }

      itemsComprados.distinct.foreach(inventario.recalcularPrecioReferencia)

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
   * Ajuste de entrada: hay más bienes o dinero que lo registrado (el de salida es un egreso tipo "Ajuste").
   * Los bienes entran valorizados a su PPP vigente, así el ajuste corrige el stock sin mover el costo promedio;
   * el dinero suma al saldo de la cuenta. Anular el ingreso revierte ambos efectos como cualquier otro ingreso.
   */
  def registrarAjusteIngreso(ingreso: IngresoRecurso, items: List[(Int, BigDecimal)], pecuniario: Option[IngresoPecuniario]): Long = {
    if (ingreso.anotaciones.forall(_.trim.isEmpty)) throw new IllegalArgumentException("Indica el motivo del ajuste")
    if (ingreso.responsableInternoId.forall(_ <= 0)) throw new IllegalArgumentException("Selecciona el responsable interno")
    if (items.isEmpty == pecuniario.isEmpty) throw new IllegalArgumentException("El ajuste debe ser de bienes (ítems) o pecuniario (cuenta), no ambos")
    items.foreach { case (itemId, cantidad) =>
      if (itemId <= 0 || cantidad <= 0) throw new IllegalArgumentException("Cada ítem del ajuste necesita un ítem del catálogo y una cantidad mayor a cero")
    }
    pecuniario.foreach { p =>
      if (p.cuentaDestinoId.forall(_ <= 0)) throw new IllegalArgumentException("Selecciona la cuenta del ajuste")
    }

    ctx.transaction {
      val detalles = items.groupMapReduce(_._1)(_._2)(_ + _).toList.sortBy(_._1).map { case (itemId, cantidad) =>
        val item = ctx.run(query[ItemCatalogo].filter(_.id == lift(itemId))).headOption
          .getOrElse(throw new IllegalArgumentException(s"Item con ID $itemId no existe"))
        val valorUnitario = item.precioPromedioPonderado.filter(_ > 0).orElse(item.precioReferencia).getOrElse(BigDecimal(0))
        DetalleIngresoRecurso(itemCatalogoId = Some(itemId), cantidad = Some(cantidad), precioUnitarioIngreso = Some(valorUnitario))
      }
      val monto = pecuniario match {
        case Some(_) => ingreso.montoTotal.filter(_ > 0).getOrElse(throw new IllegalArgumentException("El monto del ajuste debe ser mayor a cero"))
        case None => detalles.map(d => d.cantidad.getOrElse(BigDecimal(0)) * d.precioUnitarioIngreso.getOrElse(BigDecimal(0))).sum
      }

      val cabecera = ingreso.copy(
        id = 0,
        origenEntidadId = ingreso.origenEntidadId.filter(_ > 0),
        tipoTransaccion = Some("Ajuste"),
        estado = Some("Cerrado"),
        montoTotal = Some(monto),
        fecha = ingreso.fecha.orElse(Some(java.time.LocalDate.now())),
        anotaciones = ingreso.anotaciones.map(_.trim)
      )
      val ingresoId = ctx.run(query[IngresoRecurso].insertValue(lift(cabecera)).returningGenerated(_.id))

      detalles.foreach { d =>
        val detalle = d.copy(ingresoId = Some(ingresoId))
        aplicarDiferenciaIngreso(detalle, revertir = false)
        ctx.run(
          query[DetalleIngresoRecurso].insert(
            _.ingresoId -> lift(detalle.ingresoId),
            _.itemCatalogoId -> lift(detalle.itemCatalogoId),
            _.cantidad -> lift(detalle.cantidad),
            _.precioUnitarioIngreso -> lift(detalle.precioUnitarioIngreso)
          )
        )
      }
      pecuniario.foreach { p =>
        ctx.run(query[IngresoPecuniario].insertValue(lift(p.copy(ingresoId = Some(ingresoId)))))
        p.cuentaDestinoId.foreach(cuentaId => ajustarSaldoCuenta(cuentaId, monto))
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
      val esAjuste = ingreso.tipoTransaccion.exists(_.equalsIgnoreCase("Ajuste"))
      val tipo =
        if (esAjuste) { if (detallesMap.getOrElse(id, 0) > 0) "AjusteBienes" else "AjustePecuniario" }
        else if (compras.contains(id)) "Compra"
        else if (subvenciones.contains(id)) "Subvencion"
        else if (donaciones.contains(id) && detallesMap.getOrElse(id, 0) > 0) "DonacionBienes"
        else if (donaciones.contains(id)) "DonacionPecuniaria"
        else if (pecuniarios.contains(id)) "IngresoPecuniario"
        else ingreso.tipoTransaccion.getOrElse("Ingreso")

      val descripcion =
        if (esAjuste) ingreso.anotaciones
        else if (compras.contains(id)) descripcionCompra.get(id)
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

  def obtenerIngresoDetalle(id: Int): Option[IngresoDetalle] = {
    ctx.run(query[IngresoRecurso].filter(_.id == lift(id))).headOption.map { ingreso =>
      IngresoDetalle(
        ingreso = ingreso,
        donacion = ctx.run(query[IngresoDonacion].filter(_.ingresoId == lift(Option(id)))).headOption,
        compra = ctx.run(query[IngresoCompra].filter(_.ingresoId == lift(Option(id)))).headOption,
        subvencion = ctx.run(query[IngresoSubvencion].filter(_.ingresoId == lift(Option(id)))).headOption,
        pecuniario = ctx.run(query[IngresoPecuniario].filter(_.ingresoId == lift(Option(id)))).headOption,
        detalles = ctx.run(query[DetalleIngresoRecurso].filter(_.ingresoId == lift(Option(id))))
      )
    }
  }

  def actualizarIngreso(
    id: Int,
    ingreso: IngresoRecurso,
    donacion: Option[IngresoDonacion],
    compra: Option[IngresoCompra],
    subvencion: Option[IngresoSubvencion],
    detalles: List[DetalleIngresoRecurso]
  ): Long = {
    ctx.transaction {
      val actual = ctx.run(query[IngresoRecurso].filter(_.id == lift(id))).headOption.getOrElse(
        throw new NoSuchElementException(s"Ingreso con ID $id no encontrado")
      )
      if (actual.estado.exists(_.equalsIgnoreCase("Anulado"))) {
        throw new IllegalArgumentException("No se puede editar un ingreso anulado")
      }

      def validarEntidad(campo: String, valor: Option[Int]): Unit = valor.foreach { entidadId =>
        if (entidadId <= 0) throw new IllegalArgumentException(s"$campo inválido")
        val existe = ctx.run(query[cl.familiarenacer.sga.modelos.Entidad].filter(_.id == lift(entidadId)).map(_.id)).nonEmpty
        if (!existe) throw new IllegalArgumentException(s"$campo no existe (ID $entidadId)")
      }
      validarEntidad("Origen", ingreso.origenEntidadId)
      validarEntidad("Responsable interno", ingreso.responsableInternoId)
      donacion.foreach(d => validarEntidad("Gestor", d.gestorId))

      // Texto vacío borra el campo; ausente lo conserva.
      def textoEditado(nuevo: Option[String], actualValor: Option[String]): Option[String] =
        nuevo match {
          case Some(texto) => Option(texto.trim).filter(_.nonEmpty)
          case None => actualValor
        }

      val montoNuevo = if (detalles.nonEmpty) {
        detalles.map(d => d.cantidad.getOrElse(BigDecimal(0)) * d.precioUnitarioIngreso.getOrElse(BigDecimal(0))).sum
      } else ingreso.montoTotal.orElse(actual.montoTotal).getOrElse(BigDecimal(0))

      val filas = ctx.run(
        query[IngresoRecurso]
          .filter(_.id == lift(id))
          .update(
            _.origenEntidadId -> lift(ingreso.origenEntidadId.orElse(actual.origenEntidadId)),
            _.responsableInternoId -> lift(ingreso.responsableInternoId.orElse(actual.responsableInternoId)),
            _.solicitudId -> lift(ingreso.solicitudId.orElse(actual.solicitudId)),
            _.fecha -> lift(ingreso.fecha.orElse(actual.fecha)),
            // El tipo define qué tablas hijas existen: no se cambia al editar.
            _.tipoTransaccion -> lift(actual.tipoTransaccion),
            _.montoTotal -> lift(Option(montoNuevo)),
            _.estado -> lift(ingreso.estado.orElse(actual.estado)),
            _.anotaciones -> lift(textoEditado(ingreso.anotaciones, actual.anotaciones)),
            _.creadoPorId -> lift(ingreso.creadoPorId.orElse(actual.creadoPorId))
          )
      )

      donacion.foreach { d =>
        ctx.run(query[IngresoDonacion].filter(_.ingresoId == lift(Option(id))).update(
          _.propositoEspecifico -> lift(d.propositoEspecifico.map(_.trim).filter(_.nonEmpty)),
          _.gestorId -> lift(d.gestorId)
        ))
      }
      compra.foreach { c =>
        ctx.run(query[IngresoCompra].filter(_.ingresoId == lift(Option(id))).update(
          _.cuentaOrigenId -> lift(c.cuentaOrigenId),
          _.numeroFacturaBoleta -> lift(c.numeroFacturaBoleta.map(_.trim).filter(_.nonEmpty)),
          _.montoNeto -> lift(c.montoNeto),
          _.montoIva -> lift(c.montoIva)
        ))
      }
      subvencion.foreach { s =>
        ctx.run(query[IngresoSubvencion].filter(_.ingresoId == lift(Option(id))).update(
          _.nombreProyecto -> lift(s.nombreProyecto.map(_.trim).filter(_.nonEmpty)),
          _.fechaRendicionLimite -> lift(s.fechaRendicionLimite)
        ))
      }

      if (detalles.nonEmpty) {
        val detallesActuales = ctx.run(query[DetalleIngresoRecurso].filter(_.ingresoId == lift(Option(id))))
        detallesActuales.foreach { detalleActual =>
          val itemId = detalleActual.itemCatalogoId.getOrElse(throw new IllegalArgumentException("Detalle actual sin itemCatalogoId"))
          if (existeEgresoPosterior(itemId, actual.fecha, id)) {
            throw new IllegalArgumentException(s"No se puede editar el item $itemId porque tiene egresos posteriores valorizados por PPP")
          }
          aplicarDiferenciaIngreso(detalleActual, revertir = true)
        }
        ctx.run(query[DetalleIngresoRecurso].filter(_.ingresoId == lift(Option(id))).delete)

        detalles.foreach { detalle =>
          val normalizado = detalle.copy(ingresoId = Some(id))
          aplicarDiferenciaIngreso(normalizado, revertir = false)
          ctx.run(query[DetalleIngresoRecurso].insert(
            _.ingresoId -> lift(Option(id)),
            _.itemCatalogoId -> lift(normalizado.itemCatalogoId),
            _.cantidad -> lift(normalizado.cantidad),
            _.precioUnitarioIngreso -> lift(normalizado.precioUnitarioIngreso)
          ))
        }

        val esCompra = ctx.run(query[IngresoCompra].filter(_.ingresoId == lift(Option(id))).nonEmpty)
        if (esCompra) (detallesActuales ++ detalles).flatMap(_.itemCatalogoId).distinct.foreach(inventario.recalcularPrecioReferencia)
      }

      filas
    }
  }

  def anularIngreso(id: Int): Boolean = {
    ctx.transaction {
      val detalle = obtenerIngresoDetalle(id).getOrElse(throw new NoSuchElementException(s"Ingreso con ID $id no encontrado"))
      if (detalle.ingreso.estado.exists(_.equalsIgnoreCase("Anulado"))) {
        throw new IllegalArgumentException("El ingreso ya está anulado")
      }

      detalle.detalles.foreach { d =>
        val itemId = d.itemCatalogoId.getOrElse(throw new IllegalArgumentException("Detalle sin itemCatalogoId"))
        if (existeEgresoPosterior(itemId, detalle.ingreso.fecha, id)) {
          throw new IllegalArgumentException(s"No se puede anular el ingreso porque el item $itemId ya tuvo egresos posteriores")
        }
      }

      detalle.pecuniario.foreach { pec =>
        pec.cuentaDestinoId.foreach(cuentaId => ajustarSaldoCuenta(cuentaId, -detalle.ingreso.montoTotal.getOrElse(BigDecimal(0))))
      }
      detalle.compra.foreach { compra =>
        compra.cuentaOrigenId.foreach(cuentaId => ajustarSaldoCuenta(cuentaId, detalle.ingreso.montoTotal.getOrElse(BigDecimal(0))))
        anularEgresoAutomaticoCompra(id)
      }
      detalle.detalles.foreach(d => aplicarDiferenciaIngreso(d, revertir = true))

      val anulado = ctx.run(query[IngresoRecurso].filter(_.id == lift(id)).update(_.estado -> lift(Option("Anulado")))) > 0
      // La compra anulada deja de contar para la mediana del precio de referencia.
      if (detalle.compra.isDefined) detalle.detalles.flatMap(_.itemCatalogoId).distinct.foreach(inventario.recalcularPrecioReferencia)
      anulado
    }
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

  private def ajustarSaldoCuenta(cuentaId: Int, delta: BigDecimal): Unit = {
    val cero = BigDecimal(0)
    val filas = ctx.run(
      query[CuentaFinanciera]
        .filter(_.id == lift(cuentaId))
        .update(c => c.saldoActual -> Option(c.saldoActual.getOrElse(lift(cero)) + lift(delta)))
    )
    if (filas == 0) throw new IllegalArgumentException(s"Cuenta financiera con ID $cuentaId no existe")
  }

  private def aplicarDiferenciaIngreso(detalle: DetalleIngresoRecurso, revertir: Boolean): Unit = {
    val itemId = detalle.itemCatalogoId.getOrElse(throw new IllegalArgumentException("Detalle sin itemCatalogoId"))
    val cantidad = detalle.cantidad.getOrElse(BigDecimal(0))
    val precio = detalle.precioUnitarioIngreso.getOrElse(BigDecimal(0))
    val signo = if (revertir) BigDecimal(-1) else BigDecimal(1)

    val itemActual = ctx.run(query[ItemCatalogo].filter(_.id == lift(itemId)).forUpdate).headOption.getOrElse(
      throw new IllegalArgumentException(s"Item con ID $itemId no existe")
    )
    val stockNuevo = itemActual.stockActual.getOrElse(BigDecimal(0)) + (cantidad * signo)
    val valorNuevoBruto = itemActual.valorTotalStock.getOrElse(BigDecimal(0)) + (cantidad * precio * signo)
    val valorNuevo = if (valorNuevoBruto.abs <= BigDecimal("0.0001")) BigDecimal(0) else valorNuevoBruto
    if (stockNuevo < 0 || valorNuevo < 0) throw new IllegalArgumentException(s"El inventario del item $itemId quedaría negativo")
    val pppNuevo = if (stockNuevo > 0) (valorNuevo / stockNuevo).setScale(4, BigDecimal.RoundingMode.HALF_UP) else BigDecimal(0)
    ctx.run(query[ItemCatalogo].filter(_.id == lift(itemId)).update(
      _.stockActual -> lift(Option(stockNuevo)),
      _.valorTotalStock -> lift(Option(valorNuevo)),
      _.precioPromedioPonderado -> lift(Option(pppNuevo))
    ))
  }

  private def existeEgresoPosterior(itemId: Int, fechaIngreso: Option[java.time.LocalDate], ingresoId: Int): Boolean = {
    val conn = abrirConexion()
    try {
      val ps = conn.prepareStatement(
        """
          SELECT 1
          FROM detalle_egreso_recurso der
          JOIN egreso_recurso er ON er.id = der.egreso_id
          JOIN ingreso_recurso ir_actual ON ir_actual.id = ?
          WHERE der.item_catalogo_id = ?
            AND COALESCE(er.estado, 'Cerrado') <> 'Anulado'
            AND (
              er.fecha > ir_actual.fecha
              OR (er.fecha = ir_actual.fecha AND COALESCE(er.created_at, er.fecha::timestamp) > COALESCE(ir_actual.created_at, ir_actual.fecha::timestamp))
            )
          LIMIT 1
        """
      )
      ps.setInt(1, ingresoId)
      ps.setInt(2, itemId)
      val rs = ps.executeQuery()
      try rs.next()
      finally {
        rs.close()
        ps.close()
      }
    } finally conn.close()
  }

  private def anularEgresoAutomaticoCompra(ingresoId: Int): Unit = {
    val egresos = ctx.run(
      query[EgresoRecurso]
        .filter(e => e.propositoEspecifico == lift(Option(s"Compra asociada al ingreso $ingresoId")))
    )
    egresos.foreach { egreso =>
      ctx.run(query[EgresoRecurso].filter(_.id == lift(egreso.id)).update(_.estado -> lift(Option("Anulado"))))
    }
  }

  private def abrirConexion(): java.sql.Connection = {
    val host = sys.env.getOrElse("DATABASE_HOST", "localhost")
    val port = sys.env.getOrElse("DATABASE_PORT", "5432")
    val database = sys.env.getOrElse("DATABASE_NAME", "sga_renacer")
    val user = sys.env.getOrElse("DATABASE_USER", "postgres")
    val password = sys.env.getOrElse("DATABASE_PASSWORD", "")
    DriverManager.getConnection(s"jdbc:postgresql://$host:$port/$database", user, password)
  }

  private def asegurarColumnasEgresos(): Unit = {
    val conn = abrirConexion()
    try {
      val statement = conn.createStatement()
      try statement.execute("ALTER TABLE egreso_recurso ADD COLUMN IF NOT EXISTS estado VARCHAR(50) DEFAULT 'Cerrado'")
      finally statement.close()
    } finally conn.close()
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
      tipoEgreso = Some("Pago Compra"),
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
