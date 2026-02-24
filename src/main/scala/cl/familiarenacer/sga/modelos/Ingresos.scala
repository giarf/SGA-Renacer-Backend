package cl.familiarenacer.sga.modelos

import java.time.LocalDate

/**
 * Representa una transacción de ingreso de recursos.
 * Puede ser dinero, bienes comprados, donaciones, etc.
 *
 * @param id Identificador único del ingreso.
 * @param origenEntidadId ID de la entidad que provee el recurso (proveedor, donante, etc.).
 * @param responsableInternoId ID de la persona responsable de gestionar el ingreso.
 * @param solicitudId Solicitud asociada al ingreso (si aplica).
 * @param fecha Fecha de la transacción.
 * @param tipoTransaccion Tipo de ingreso (compra, donación, subvención, etc.).
 * @param montoTotal Valor monetario total asociado.
 * @param estado Estado del ingreso (abierto, cerrado, anulado).
 * @param creadoPorId ID del trabajador que registró el ingreso.
 */
case class IngresoRecurso(
  id: Int,
  origenEntidadId: Option[Int] = None,
  responsableInternoId: Option[Int] = None,
  solicitudId: Option[Int] = None,
  fecha: Option[LocalDate] = None,
  tipoTransaccion: Option[String] = None,
  montoTotal: Option[BigDecimal] = None,
  estado: Option[String] = None,
  creadoPorId: Option[Int] = None
)

/**
 * Detalle específico para ingreso de dinero (Pecuniario).
 *
 * @param ingresoId Identificador del ingreso, referencia a IngresoRecurso.id.
 * @param cuentaDestinoId ID de la cuenta donde se deposita el dinero.
 * @param metodoTransferencia Método de pago (transferencia, efectivo, cheque).
 */
case class IngresoPecuniario(
  ingresoId: Option[Int] = None,
  cuentaDestinoId: Option[Int] = None,
  metodoTransferencia: Option[String] = None
)

/**
 * Detalle específico para ingreso por compra de bienes/servicios.
 *
 * @param ingresoId Identificador del ingreso.
 * @param cuentaOrigenId ID de la cuenta desde donde se pagó.
 * @param numeroFacturaBoleta Número de documento tributario.
 * @param montoNeto Monto neto de la compra.
 * @param montoIva Monto del IVA.
 */
case class IngresoCompra(
  ingresoId: Option[Int] = None,
  cuentaOrigenId: Option[Int] = None,
  numeroFacturaBoleta: Option[String] = None,
  montoNeto: Option[BigDecimal] = None,
  montoIva: Option[BigDecimal] = None
)

/**
 * Detalle específico para ingreso por donación.
 *
 * @param ingresoId Identificador del ingreso.
 * @param numeroCertificado Número de certificado de donación emitido.
 * @param propositoEspecifico Propósito o restricción de uso de la donación.
 */
case class IngresoDonacion(
  ingresoId: Option[Int] = None,
  numeroCertificado: Option[String] = None,
  propositoEspecifico: Option[String] = None
)

/**
 * Detalle específico para ingreso por subvención estatal o privada.
 *
 * @param ingresoId Identificador del ingreso.
 * @param nombreProyecto Nombre del proyecto subvencionado.
 * @param fechaRendicionLimite Fecha límite para rendir cuentas.
 */
case class IngresoSubvencion(
  ingresoId: Option[Int] = None,
  nombreProyecto: Option[String] = None,
  fechaRendicionLimite: Option[LocalDate] = None
)

/**
 * Detalle de los ítems ingresados físicamente (si aplica).
 *
 * @param id Identificador único del detalle.
 * @param ingresoId ID del ingreso asociado.
 * @param itemCatalogoId ID del ítem ingresado.
 * @param cantidad Cantidad ingresada.
 * @param precioUnitarioIngreso Costo unitario al momento del ingreso.
 */
case class DetalleIngresoRecurso(
  id: Int,
  ingresoId: Option[Int] = None,
  itemCatalogoId: Option[Int] = None,
  cantidad: Option[BigDecimal] = None,
  precioUnitarioIngreso: Option[BigDecimal] = None
)
