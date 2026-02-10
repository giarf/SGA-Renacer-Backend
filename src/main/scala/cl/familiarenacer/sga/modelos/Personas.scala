package cl.familiarenacer.sga.modelos

import java.time.LocalDate

/**
 * Representa a una persona natural.
 * Extiende de Entidad compartiendo su misma ID.
 *
 * @param entidadId Identificador único, referencia a Entidad.id.
 * @param nombres Nombres de la persona (Obligatorio).
 * @param apellidos Apellidos de la persona.
 * @param genero Género de la persona.
 */
case class PersonaNatural(
  entidadId: Int,
  nombres: String,
  apellidos: Option[String],
  genero: Option[String]
)

/**
 * Representa a una institución u organización.
 * Extiende de Entidad compartiendo su misma ID.
 *
 * @param entidadId Identificador único, referencia a Entidad.id.
 * @param razonSocial Razón social de la institución (Obligatorio).
 * @param nombreFantasia Nombre de fantasía o comercial.
 * @param subtipoInstitucion Tipo específico (ONG, Empresa, etc.).
 * @param rubro Rubro o actividad económica.
 */
case class Institucion(
  entidadId: Int,
  razonSocial: String,
  nombreFantasia: Option[String],
  subtipoInstitucion: Option[String],
  rubro: Option[String]
)

/**
 * Rol de Beneficiario asignado a una persona.
 * Recibe ayuda social de la fundación.
 *
 * @param personaId Identificador de la persona, referencia a PersonaNatural.entidadId.
 * @param familiaId Familia a la que pertenece.
 * @param fechaNacimiento Fecha de nacimiento.
 * @param escolaridad Nivel de escolaridad.
 * @param tallaRopa Talla de ropa para donaciones.
 * @param observacionesMedicas Observaciones de salud relevantes.
 */
case class Beneficiario(
  personaId: Int,
  familiaId: Option[Int],
  fechaNacimiento: Option[LocalDate],
  escolaridad: Option[String],
  tallaRopa: Option[String],
  observacionesMedicas: Option[String]
)

/**
 * Rol de Colaborador asignado a una persona.
 * Ayuda en actividades o realiza donaciones.
 *
 * @param personaId Identificador de la persona.
 * @param tipoColaborador Tipo de colaboración.
 * @param esAnonimo Si el colaborador prefiere anonimato.
 */
case class Colaborador(
  personaId: Int,
  tipoColaborador: Option[String],
  esAnonimo: Option[Boolean]
)

/**
 * Rol de Trabajador de la fundación.
 *
 * @param personaId Identificador de la persona.
 * @param cargo Cargo que desempeña.
 * @param fechaIngreso Fecha de ingreso a la fundación.
 */
case class Trabajador(
  personaId: Int,
  cargo: Option[String],
  fechaIngreso: Option[LocalDate]
)

/**
 * Rol Directivo de la fundación.
 * Autoriza solicitudes y gestiona recursos.
 *
 * @param personaId Identificador de la persona.
 * @param cargo Cargo directivo.
 * @param firmaDigitalUrl URL de la imagen de su firma digital.
 */
case class Directivo(
  personaId: Int,
  cargo: Option[String],
  firmaDigitalUrl: Option[String]
)
