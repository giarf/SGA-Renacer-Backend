package cl.familiarenacer.sga.modelos

/**
 * Data Transfer Object (DTO) para enviar resúmenes de entidades a la vista.
 * Unifica la información de Personas e Instituciones en una sola estructura plana.
 *
 * @param id ID único de la entidad.
 * @param identificador RUT o identificador principal.
 * @param nombreCompleto Nombres + Apellidos (Persona) o Razón Social (Institución).
 * @param tipoEntidad Tipo de entidad (Persona, Institucion, etc.).
 * @param correo Correo de contacto (Opcional).
 * @param telefono Teléfono de contacto (Opcional).
 * @param direccion Dirección física (Opcional).
 * @param comuna Comuna (Opcional).
 * @param genero Género (Solo para Personas Naturales, Opcional).
 */
case class EntidadResumen(
  id: Int,
  identificador: String,
  nombreCompleto: String
)

/**
 * Resumen para listar ingresos en el historial.
 *
 * @param id ID del ingreso.
 * @param fecha Fecha de registro o contabilización.
 * @param tipo Valor semántico (DonacionBienes, DonacionPecuniaria, Compra, etc.).
 * @param montoTotal Monto asociado (si aplica).
 * @param estado Estado administrativo del ingreso.
 * @param descripcion Texto auxiliar (propósito, factura, proyecto, etc.).
 */
case class IngresoHistorial(
  id: Int,
  fecha: Option[java.time.LocalDate],
  tipo: String,
  montoTotal: Option[BigDecimal],
  estado: Option[String],
  descripcion: Option[String]
)
