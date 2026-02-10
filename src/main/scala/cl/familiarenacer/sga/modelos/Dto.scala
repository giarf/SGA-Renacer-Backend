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
  nombreCompleto: String,
  tipoEntidad: String,
  correo: Option[String],
  telefono: Option[String],
  direccion: Option[String],
  comuna: Option[String],
  genero: Option[String]
)
