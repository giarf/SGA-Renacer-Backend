package cl.familiarenacer.sga.modelos

/**
 * Representa un producto o recurso gestionable en el inventario.
 *
 * @param id Identificador único del ítem.
 * @param nombre Nombre único del producto.
 * @param categoria Categoría para agrupar ítems (ej. "Alimentos", "Construcción").
 * @param unidadMedidaEstandar Unidad de medida (Kilos, Litros, Unidades).
 * @param stockActual Cantidad actual en existencia.
 * @param valorTotalStock Valor monetario total del stock actual.
 * @param precioPromedioPonderado Costo promedio de adquisición del ítem.
 * @param precioReferencia Precio de mercado referencial.
 */
case class ItemCatalogo(
  id: Int,
  nombre: Option[String],
  categoria: Option[String],
  unidadMedidaEstandar: Option[String],
  stockActual: Option[BigDecimal],
  valorTotalStock: Option[BigDecimal],
  precioPromedioPonderado: Option[BigDecimal],
  precioReferencia: Option[BigDecimal]
)

/**
 * Estructura auxiliar para pasar los datos completos de un ítem en una donación.
 * Combina el detalle técnico (tabla) con los descriptores (nombre, categoría) necesarios para crear el ítem si no existe.
 */
case class DetalleDonacionInput(
  detalle: DetalleIngresoRecurso,
  nombreItem: String,
  categoria: Option[String],
  unidadMedida: Option[String]
)
