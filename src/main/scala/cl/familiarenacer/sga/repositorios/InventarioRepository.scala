package cl.familiarenacer.sga.repositorios

import cl.familiarenacer.sga.modelos.{CodigoBarras, CodigoBarrasDuplicadoException, DetalleIngresoRecurso, DetalleDonacionInput, IngresoDonacion, IngresoRecurso, ItemCatalogo, OrigenDatos}
import java.time.LocalDate
import scala.util.Using
import io.getquill._

/**
 * Repositorio para la gestión del inventario y cálculo de costos.
 */
class InventarioRepository(val ctx: PostgresJdbcContext[SnakeCase.type]) {
  import ctx._

  /** Aplica la parte de esquema de catalogo-codigo-barras.sql (sin el backfill). */
  def asegurarEsquemaCatalogo(): Unit = {
    val stream = Option(getClass.getResourceAsStream("/catalogo-codigo-barras.sql"))
      .getOrElse(throw new IllegalStateException("Falta catalogo-codigo-barras.sql"))
    val sql = Using.resource(scala.io.Source.fromInputStream(stream, "UTF-8"))(_.mkString).split("-- BACKFILL")(0)
    Using.resource(ctx.dataSource.getConnection) { conn =>
      Using.resource(conn.createStatement())(_.execute(sql))
    }
    ()
  }

  // ===== Código de barras y precio de referencia =====

  /** Valida y normaliza un código; lanza IllegalArgumentException si el formato o el verificador fallan. */
  def validarCodigo(codigo: String): String =
    CodigoBarras.normalizar(codigo).getOrElse(throw new IllegalArgumentException("Código de barras inválido"))

  def buscarPorCodigo(codigo: String): Option[ItemCatalogo] =
    ctx.run(query[ItemCatalogo].filter(_.codigoBarras.contains(lift(codigo)))).headOption

  /** Lanza CodigoBarrasDuplicadoException si otro ítem (distinto de exceptoId) ya usa el código. */
  def asegurarCodigoLibre(codigo: String, exceptoId: Int = 0): Unit =
    if (ctx.run(query[ItemCatalogo].filter(i => i.codigoBarras.contains(lift(codigo)) && i.id != lift(exceptoId)).nonEmpty))
      throw new CodigoBarrasDuplicadoException(codigo)

  /**
   * Precio de referencia = mediana del precio unitario de las compras no anuladas de los últimos 180 días,
   * redondeada a pesos. Si no hay compras en ese rango, conserva el valor existente.
   * Usa la conexión de la transacción en curso cuando se llama dentro de ctx.transaction.
   */
  def recalcularPrecioReferencia(itemId: Int): Long =
    ctx.run(quote {
      infix"""UPDATE item_catalogo ic
        SET precio_referencia = m.mediana, precio_referencia_fecha = CURRENT_DATE
        FROM (
          SELECT round((percentile_cont(0.5) WITHIN GROUP (ORDER BY d.precio_unitario_ingreso))::numeric, 0) AS mediana
          FROM detalle_ingreso_recurso d
          JOIN ingreso_recurso ir ON ir.id = d.ingreso_id
          JOIN ingreso_compra c ON c.ingreso_id = ir.id
          WHERE d.item_catalogo_id = ${lift(itemId)}
            AND d.precio_unitario_ingreso IS NOT NULL
            AND lower(coalesce(ir.estado, '')) <> 'anulado'
            AND ir.fecha >= CURRENT_DATE - 180
          HAVING count(*) > 0
        ) m
        WHERE ic.id = ${lift(itemId)}""".as[Update[ItemCatalogo]]
    })

  /**
   * Obtiene (con lock) o crea el ítem de un detalle de ingreso.
   * 1) itemCatalogoId; si el ítem aún no tiene código, se le asigna el recibido.
   * 2) codigoBarras; 3) nombre (sin tildes ni mayúsculas); 4) se crea con el código y el origen dado.
   * Debe llamarse dentro de una transacción.
   */
  def resolverItem(input: DetalleDonacionInput, origen: String): Int = {
    val codigo = input.codigoBarras.map(_.trim).filter(_.nonEmpty).map(validarCodigo)
    val precio = input.detalle.precioUnitarioIngreso
    def bloquear(id: Int) = ctx.run(query[ItemCatalogo].filter(_.id == lift(id)).forUpdate).headOption
    def asignarCodigoSiFalta(item: ItemCatalogo): Int = {
      codigo.filter(_ => item.codigoBarras.forall(_.isEmpty)).foreach { c =>
        asegurarCodigoLibre(c, item.id)
        ctx.run(query[ItemCatalogo].filter(_.id == lift(item.id)).update(_.codigoBarras -> lift(Option(c))))
      }
      item.id
    }

    input.detalle.itemCatalogoId.filter(_ > 0) match {
      case Some(id) =>
        asignarCodigoSiFalta(bloquear(id).getOrElse(throw new IllegalArgumentException(s"Item con ID $id no existe")))
      case None =>
        val nombre = input.nombreItem.trim
        val porCodigo = codigo.flatMap(c => ctx.run(query[ItemCatalogo].filter(_.codigoBarras.contains(lift(c))).forUpdate).headOption)
        def porNombre = Option(nombre).filter(_.nonEmpty).flatMap { n =>
          ctx.run(
            query[ItemCatalogo]
              .filter(i =>
                infix"unaccent(lower(${i.nombre}))".as[Option[String]] ==
                infix"unaccent(lower(${lift(Option(n))}))".as[Option[String]]
              )
              .forUpdate
          ).headOption
        }
        porCodigo.map(_.id).orElse(porNombre.map(asignarCodigoSiFalta)).getOrElse {
          if (nombre.isEmpty)
            throw new IllegalArgumentException("Cada ítem debe incluir itemCatalogoId, un código de barras registrado o un nombre.")
          codigo.foreach(c => asegurarCodigoLibre(c))
          val nuevoItem = ItemCatalogo(
            id = 0,
            nombre = Some(nombre),
            categoria = input.categoria.map(_.trim).filter(_.nonEmpty),
            unidadMedidaEstandar = input.unidadMedida.map(_.trim).filter(_.nonEmpty),
            stockActual = Some(BigDecimal(0)),
            valorTotalStock = Some(BigDecimal(0)),
            precioPromedioPonderado = Some(BigDecimal(0)),
            precioReferencia = precio, // Referencia inicial
            codigoBarras = codigo,
            origenDatos = Some(origen),
            precioReferenciaFecha = precio.map(_ => LocalDate.now())
          )
          ctx.run(query[ItemCatalogo].insertValue(lift(nuevoItem)).returningGenerated(_.id))
        }
    }
  }

  // ===== Donación de Bienes (con upsert de catálogo y recálculo de PPP) =====

  def registrarDonacionCompleta(ingreso: IngresoRecurso, donacion: IngresoDonacion, items: List[DetalleDonacionInput]): Long = {
     // 1. Validaciones
    if (items.isEmpty) throw new IllegalArgumentException("Debe haber al menos un ítem.")
    val montoCalculado = items.map(i => i.detalle.cantidad.getOrElse(BigDecimal(0)) * i.detalle.precioUnitarioIngreso.getOrElse(BigDecimal(0))).sum
    val montoIngreso = ingreso.montoTotal.getOrElse(BigDecimal(0))
    if ((montoIngreso - montoCalculado).abs > BigDecimal(0.01)) {
       throw new IllegalArgumentException(s"Monto total no coincide. Cabecera: $montoIngreso, Suma Detalles: $montoCalculado")
    }

    ctx.transaction {
      val ingresoId = ctx.run(query[IngresoRecurso].insertValue(lift(ingreso)).returningGenerated(_.id))
      ctx.run(query[IngresoDonacion].insertValue(lift(donacion.copy(ingresoId = Some(ingresoId)))))

      items.foreach { itemInput =>
        val detalle = itemInput.detalle
        val cantidad = detalle.cantidad.getOrElse(BigDecimal(0))
        val precio = detalle.precioUnitarioIngreso.getOrElse(BigDecimal(0))
        
        if (cantidad <= 0) throw new IllegalArgumentException(s"Cantidad inválida para el ítem '${itemInput.nombreItem}'")

        // 2. Obtener o crear el ítem (por ID, código de barras o nombre) con lock pesimista.
        val itemId = resolverItem(itemInput, OrigenDatos.Donacion)

        // 3. Recalcular Stock y PPP
        // Ahora recuperamos el item (ya sea existente o recién creado) para actualizar stock
        val itemActual = ctx.run(query[ItemCatalogo].filter(_.id == lift(itemId))).headOption
          .getOrElse(throw new IllegalArgumentException(s"Error crítico: Item $itemId desapareció"))

        val stockActual = itemActual.stockActual.getOrElse(BigDecimal(0))
        val valorTotalActual = itemActual.valorTotalStock.getOrElse(BigDecimal(0))

        val nuevoStock = stockActual + cantidad
        // PPP: (ValorTotalActual + (Cant * Precio)) / NuevoStock
        val valorIngreso = cantidad * precio
        val nuevoValorTotal = valorTotalActual + valorIngreso
        
        val nuevoPPP = if (nuevoStock > 0) {
           (nuevoValorTotal / nuevoStock)
             .setScale(4, BigDecimal.RoundingMode.HALF_UP)
        } else BigDecimal(0)

        // 4. Actualizar Ítem
        ctx.run(query[ItemCatalogo].filter(_.id == lift(itemId)).update(
          _.stockActual -> lift(Option(nuevoStock)),
          _.valorTotalStock -> lift(Option(nuevoValorTotal)),
          _.precioPromedioPonderado -> lift(Option(nuevoPPP))
        ))

        // 5. Insertar Detalle (excluimos el ID para que la DB use AUTO_INCREMENT)
        ctx.run(
          query[DetalleIngresoRecurso].insert(
            _.ingresoId -> lift(Option(ingresoId)),
            _.itemCatalogoId -> lift(Option(itemId)),
            _.cantidad -> lift(detalle.cantidad),
            _.precioUnitarioIngreso -> lift(detalle.precioUnitarioIngreso)
          )
        )
      }

      ingresoId.toLong
    }
  }

  /** Busca todas las palabras, sin distinguir orden, mayúsculas o tildes. */
  def buscarItems(queryBusqueda: String): List[ItemCatalogo] = {
    val palabras = java.text.Normalizer.normalize(queryBusqueda, java.text.Normalizer.Form.NFD)
      .replaceAll("\\p{M}+", "").toLowerCase(java.util.Locale.ROOT)
      .replaceAll("[^\\p{L}\\p{N}]+", " ").trim
    ctx.run(
      query[ItemCatalogo].filter { i =>
        infix"""
          ${lift(palabras)} <> '' AND NOT EXISTS (
            SELECT 1 FROM regexp_split_to_table(${lift(palabras)}, ' +') AS search_word(value)
            WHERE strpos(regexp_replace(unaccent(lower(
              coalesce(${i.nombre}, '') || ' ' || coalesce(${i.categoria}, '')
            )), '[^[:alnum:]]+', ' ', 'g'), search_word.value) = 0
          )
        """.as[Boolean]
      }.take(15)
    )
  }

  /**
   * Retorna listas de categorías y unidades de medida únicas existentes en el catálogo.
   * Útil para llenar selectores en el frontend.
   */
  def listarCategoriasUnidades(): (List[String], List[String]) = {
    val categorias = ctx.run(query[ItemCatalogo].map(_.categoria).distinct).flatten
    val unidades = ctx.run(query[ItemCatalogo].map(_.unidadMedidaEstandar).distinct).flatten
    (categorias, unidades)
  }

  /**
   * Lista todos los ítems del catálogo ordenados por nombre.
   * 
   * @return Lista completa de ítems con todos sus campos.
   */
  def listarTodosItems(): List[ItemCatalogo] = {
    ctx.run(
      query[ItemCatalogo]
        .sortBy(_.nombre)(Ord.asc)
    )
  }

  /**
   * Registra un nuevo ítem en el catálogo.
   * Inicializa stock y PPP en 0.
   * 
   * @param item Ítem a registrar (sin ID, se genera automáticamente).
   * @return ID del ítem creado.
   */
  def registrarItem(item: ItemCatalogo): Int = ctx.transaction {
    item.codigoBarras.foreach(c => asegurarCodigoLibre(c))
    ctx.run(
      query[ItemCatalogo].insert(
        _.nombre -> lift(item.nombre.map(_.trim)),
        _.categoria -> lift(item.categoria.map(_.trim)),
        _.unidadMedidaEstandar -> lift(item.unidadMedidaEstandar.map(_.trim)),
        _.precioReferencia -> lift(item.precioReferencia),
        _.precioReferenciaFecha -> lift(item.precioReferencia.map(_ => LocalDate.now())),
        _.codigoBarras -> lift(item.codigoBarras),
        _.origenDatos -> lift(item.origenDatos.orElse(Option(OrigenDatos.Manual))),
        _.stockActual -> lift(Option(BigDecimal(0))),
        _.precioPromedioPonderado -> lift(Option(BigDecimal(0))),
        _.valorTotalStock -> lift(Option(BigDecimal(0)))
      ).returningGenerated(_.id)
    ).toInt
  }

  /**
   * Actualiza los campos básicos de un ítem del catálogo.
   * PROTEGE los campos calculados: stockActual, precioPromedioPonderado, valorTotalStock.
   * Estos solo pueden ser modificados mediante ingresos/egresos.
   * 
   * @param id ID del ítem a actualizar.
   * @param nombre Nuevo nombre (opcional).
   * @param categoria Nueva categoría (opcional).
   * @param unidadMedida Nueva unidad de medida (opcional).
   * @param precioReferencia Nuevo precio de referencia (opcional).
   * @return Número de filas actualiz adas (1 si exitoso, 0 si no encontrado).
   */
  def actualizarItemBasico(
    id: Int,
    nombre: Option[String],
    categoria: Option[String],
    unidadMedida: Option[String],
    precioReferencia: Option[BigDecimal],
    codigoBarras: Option[Option[String]] = None
  ): Long = ctx.transaction {
    ctx.run(query[ItemCatalogo].filter(_.id == lift(id)).forUpdate).headOption match {
      case None => 0L
      case Some(actual) =>
        // codigoBarras: None = no viene en el JSON (se conserva), Some(None) = se borra.
        val nuevoCodigo = codigoBarras.getOrElse(actual.codigoBarras)
        nuevoCodigo.foreach(c => asegurarCodigoLibre(c, id))
        val fechaPrecio =
          if (precioReferencia.map(_.bigDecimal.stripTrailingZeros) == actual.precioReferencia.map(_.bigDecimal.stripTrailingZeros))
            actual.precioReferenciaFecha
          else precioReferencia.map(_ => LocalDate.now())
        ctx.run(
          query[ItemCatalogo]
            .filter(_.id == lift(id))
            .update(
              _.nombre -> lift(nombre.map(_.trim)),
              _.categoria -> lift(categoria.map(_.trim)),
              _.unidadMedidaEstandar -> lift(unidadMedida.map(_.trim)),
              _.precioReferencia -> lift(precioReferencia),
              _.precioReferenciaFecha -> lift(fechaPrecio),
              _.codigoBarras -> lift(nuevoCodigo)
            )
        )
    }
  }

  /**
   * Obtiene lista de categorías únicas del catálogo.
   * Útil para selectores en el frontend.
   * 
   * @return Lista de categorías únicas no vacías, ordenadas alfabéticamente.
   */
  def obtenerCategoriasUnicas(): List[String] = {
    ctx.run(
      query[ItemCatalogo]
        .map(_.categoria)
        .filter(_.isDefined)
        .distinct
        .sortBy(c => c)(Ord.asc)
    ).flatten
  }
}
