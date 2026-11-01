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
 * @param precioReferencia Precio de mercado referencial (mediana de compras de los últimos 180 días, o valor manual).
 * @param codigoBarras EAN-8/EAN-13/UPC-A, solo dígitos y único en el catálogo.
 * @param origenDatos De dónde salieron los datos del ítem: manual, openfoodfacts, compra o donacion.
 * @param precioReferenciaFecha Cuándo se calculó o fijó el precio de referencia.
 */
case class ItemCatalogo(
  id: Int,
  nombre: Option[String],
  categoria: Option[String],
  unidadMedidaEstandar: Option[String],
  stockActual: Option[BigDecimal],
  valorTotalStock: Option[BigDecimal],
  precioPromedioPonderado: Option[BigDecimal],
  precioReferencia: Option[BigDecimal],
  codigoBarras: Option[String] = None,
  origenDatos: Option[String] = None,
  precioReferenciaFecha: Option[java.time.LocalDate] = None
)

object OrigenDatos {
  val Manual = "manual"
  val OpenFoodFacts = "openfoodfacts"
  val Compra = "compra"
  val Donacion = "donacion"
  val validos: Set[String] = Set(Manual, OpenFoodFacts, Compra, Donacion)
}

/** Validación de códigos EAN-8, UPC-A (12) y EAN-13. */
object CodigoBarras {
  /** Quita espacios y guiones; retorna el código si tiene largo válido y dígito verificador correcto. */
  def normalizar(codigo: String): Option[String] = {
    val limpio = codigo.trim.replaceAll("[\\s-]", "")
    Some(limpio).filter(esValido)
  }

  def esValido(codigo: String): Boolean =
    codigo.matches("\\d{8}|\\d{12}|\\d{13}") && {
      val digitos = codigo.map(_ - '0')
      // Desde la derecha (sin el verificador) los pesos alternan 3, 1, 3, ...
      val suma = digitos.init.reverse.zipWithIndex.map { case (d, i) => if (i % 2 == 0) d * 3 else d }.sum
      (10 - suma % 10) % 10 == digitos.last
    }
}

class CodigoBarrasDuplicadoException(val codigo: String)
  extends RuntimeException(s"El código de barras $codigo ya está asignado a otro ítem del catálogo")

/**
 * Estructura auxiliar para pasar los datos completos de un ítem en una donación.
 * Combina el detalle técnico (tabla) con los descriptores (nombre, categoría) necesarios para crear el ítem si no existe.
 */
case class DetalleDonacionInput(
  detalle: DetalleIngresoRecurso,
  nombreItem: String,
  categoria: Option[String],
  unidadMedida: Option[String],
  codigoBarras: Option[String] = None
)
