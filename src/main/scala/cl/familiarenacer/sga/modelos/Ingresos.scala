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
  origenEntidadId: Option[Int],
  responsableInternoId: Option[Int],
  solicitudId: Option[Int],
  fecha: Option[LocalDate],
  tipoTransaccion: Option[String],
  montoTotal: Option[BigDecimal],
  estado: Option[String],
  creadoPorId: Option[Int]
)

/**
 * Detalle específico para ingreso de dinero (Pecuniario).
 *
 * @param ingresoId Identificador del ingreso, referencia a IngresoRecurso.id.
 * @param cuentaDestinoId ID de la cuenta donde se deposita el dinero.
 * @param metodoTransferencia Método de pago (transferencia, efectivo, cheque).
 */
case class IngresoPecuniario(
  ingresoId: Int,
  cuentaDestinoId: Option[Int],
  metodoTransferencia: Option[String]
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
  ingresoId: Int,
  cuentaOrigenId: Option[Int],
  numeroFacturaBoleta: Option[String],
  montoNeto: Option[BigDecimal],
  montoIva: Option[BigDecimal]
)

/**
 * Detalle específico para ingreso por donación.
 *
 * @param ingresoId Identificador del ingreso.
 * @param numeroCertificado Número de certificado de donación emitido.
 * @param propositoEspecifico Propósito o restricción de uso de la donación.
 */
case class IngresoDonacion(
  ingresoId: Int,
  numeroCertificado: Option[String],
  propositoEspecifico: Option[String]
)

/**
 * Detalle específico para ingreso por subvención estatal o privada.
 *
 * @param ingresoId Identificador del ingreso.
 * @param nombreProyecto Nombre del proyecto subvencionado.
 * @param fechaRendicionLimite Fecha límite para rendir cuentas.
 */
case class IngresoSubvencion(
  ingresoId: Int,
  nombreProyecto: Option[String],
  fechaRendicionLimite: Option[LocalDate]
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
  ingresoId: Option[Int],
  itemCatalogoId: Option[Int],
  cantidad: Option[BigDecimal],
  precioUnitarioIngreso: Option[BigDecimal]
)
