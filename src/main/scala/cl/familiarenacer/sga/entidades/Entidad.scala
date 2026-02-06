package cl.familiarenacer.sga.entidades

case class Entidad(
                    id: Int,
                    rut: Option[String],
                    tipoEntidad: String,
                    correo: Option[String],
                    telefono: Option[String]
                  )