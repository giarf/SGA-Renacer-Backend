package cl.familiarenacer.sga.modelos

import java.time.LocalDateTime

/**
 * Representa una Entidad base en el sistema.
 * Puede ser una Persona Natural o una Institución.
 * Almacena datos de contacto e identificación generales.
 *
 * @param id Identificador único de la entidad (Autoincremental).
 * @param rut Rol Único Tributario (opcional, pero único si existe).
 * @param tipoEntidad Tipo de entidad: 'Persona' o 'Institucion'.
 * @param telefono Número de contacto.
 * @param correo Correo electrónico de contacto.
 * @param direccion Dirección física.
 * @param comuna Comuna de residencia o ubicación (Por defecto 'Quillota').
 * @param createdAt Fecha y hora de creación del registro.
 */
case class Entidad(
  id: Int,
  rut: Option[String],
  tipoEntidad: Option[String],
  telefono: Option[String],
  correo: Option[String],
  direccion: Option[String],
  comuna: Option[String],
  region: Option[String] = None,
  redSocial: Option[String] = None,
  gestorId: Option[Int] = None,
  anotaciones: Option[String] = None,
  sector: Option[String] = None,
  createdAt: Option[LocalDateTime]
)

/**
 * Representa un grupo familiar beneficiario o relacionado.
 *
 * @param id Identificador único de la familia.
 * @param nombreFamilia Nombre descriptivo de la familia (ej. "Familia Pérez").
 * @param puntosVulnerabilidad Puntaje asignado para evaluar prioridad de ayuda.
 * @param jefeHogarId ID de la persona natural que actúa como jefe de hogar.
 */
case class Familia(
  id: Int = 0,
  nombreFamilia: Option[String],
  puntosVulnerabilidad: Option[Int],
  jefeHogarId: Option[Int],
  justificacionVulnerabilidad: Option[String] = None
)

case class FamiliaMiembro(
  familiaId: Int,
  personaId: Int,
  rolFamiliar: Option[String] = None,
  observaciones: Option[String] = None
)

case class FamiliaMiembroDetalle(
  id: Int,
  personaId: Int,
  nombres: String,
  apellidos: Option[String],
  rut: Option[String],
  correo: Option[String] = None,
  telefono: Option[String] = None,
  fotoUrl: Option[String] = None,
  rolFamiliar: Option[String] = None,
  observaciones: Option[String] = None
)

case class Etiqueta(
  id: Int,
  nombre: String,
  slug: String,
  descripcion: Option[String] = None,
  color: Option[String] = None,
  activa: Boolean = true
)

case class EntidadEtiqueta(
  entidadId: Int,
  etiquetaId: Int
)

case class Region(
  id: Int,
  nombre: String
)

case class Comuna(
  id: Int,
  regionId: Int,
  nombre: String
)

/**
 * Representa una cuenta financiera de la organización.
 * Se utiliza para rastrear saldos y movimientos de dinero.
 *
 * @param id Identificador único de la cuenta.
 * @param nombre Nombre de la cuenta (ej. "Banco Estado", "Caja Chica").
 * @param saldoActual Saldo actual disponible en la cuenta.
 */
case class CuentaFinanciera(
  id: Int = 0,
  nombre: Option[String],
  saldoActual: Option[BigDecimal]
)
