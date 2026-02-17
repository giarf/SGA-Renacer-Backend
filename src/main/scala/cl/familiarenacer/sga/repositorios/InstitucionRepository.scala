package cl.familiarenacer.sga.repositorios

import cl.familiarenacer.sga.modelos.{Entidad, Institucion}
import io.getquill._

/**
 * Repositorio para manejar la persistencia de Instituciones.
 * Similar a EntidadRepository pero específico para instituciones.
 *
 * @param ctx Contexto de Quill inyectado (generalmente DB.ctx).
 */
class InstitucionRepository(val ctx: PostgresJdbcContext[SnakeCase.type]) {
  import ctx._

  /**
   * Registra una nueva Institución en el sistema de forma transaccional.
   * La operación es atómica: primero crea la Entidad base y luego la Institución asociada.
   *
   * @param institucion Datos de la institución a registrar (sin ID todavía).
   * @param entidad Datos base de la entidad (contacto, dirección, etc.).
   * @return El ID generado para la nueva entidad/institución.
   */
  def registrarInstitucion(institucion: Institucion, entidad: Entidad): Long = {
    ctx.transaction {
      // 1. Insertamos la Entidad y recuperamos el ID generado autoincrementalmente.
      val entidadId = ctx.run(
        query[Entidad]
          .insertValue(lift(entidad))
          .returningGenerated(_.id)
      )

      // 2. Asociamos el ID generado a la Institución.
      val institucionConId = institucion.copy(entidadId = entidadId)

      // 3. Insertamos la Institución.
      ctx.run(
        query[Institucion].insertValue(lift(institucionConId))
      )

      // Retornamos el ID generado.
      entidadId.toLong
    }
  }

  /**
   * Obtiene los datos completos de una Institución (Entidad + Institucion).
   *
   * @param entidadId ID de la entidad/institución a obtener.
   * @return Option con tupla (Entidad, Institucion) si existe, None si no.
   */
  def obtenerInstitucionCompleta(entidadId: Int): Option[(Entidad, Institucion)] = {
    ctx.run(
      query[Entidad]
        .filter(_.id == lift(entidadId))
        .join(query[Institucion]).on((e, i) => e.id == i.entidadId)
    ).headOption
  }

  /**
   * Lista todas las Instituciones con sus datos de Entidad.
   */
  def listarTodasLasInstituciones(): List[(Entidad, Institucion)] = {
    val q = quote {
      query[Entidad]
        .join(query[Institucion])
        .on(_.id == _.entidadId)
    }
    ctx.run(q)
  }

  /**
   * Actualiza una Institución y su Entidad asociada de forma transaccional.
   *
   * @param entidadId ID de la entidad a actualizar.
   * @param institucion Datos actualizados de la institución.
   * @param entidad Datos actualizados de la entidad base.
   * @return El número de filas actualizadas.
   */
  def actualizarInstitucion(entidadId: Int, institucion: Institucion, entidad: Entidad): Long = {
    ctx.transaction {
      // 1. Actualizar la Entidad
      val entidadRows = ctx.run(
        query[Entidad]
          .filter(_.id == lift(entidadId))
          .update(
            _.rut -> lift(entidad.rut),
            _.tipoEntidad -> lift(entidad.tipoEntidad),
            _.telefono -> lift(entidad.telefono),
            _.correo -> lift(entidad.correo),
            _.direccion -> lift(entidad.direccion),
            _.comuna -> lift(entidad.comuna),
            _.redSocial -> lift(entidad.redSocial),
            _.gestorId -> lift(entidad.gestorId),
            _.anotaciones -> lift(entidad.anotaciones),
            _.sector -> lift(entidad.sector)
          )
      )

      // 2. Actualizar la Institución
      val institucionRows = ctx.run(
        query[Institucion]
          .filter(_.entidadId == lift(entidadId))
          .update(
            _.razonSocial -> lift(institucion.razonSocial),
            _.nombreFantasia -> lift(institucion.nombreFantasia),
            _.subtipoInstitucion -> lift(institucion.subtipoInstitucion),
            _.rubro -> lift(institucion.rubro)
          )
      )

      (entidadRows + institucionRows).toLong
    }
  }

  /**
   * Elimina una Institución y su Entidad base de forma transaccional.
   *
   * @param id ID de la entidad/institución a eliminar.
   * @return true si se eliminó correctamente, false si no existía.
   */
  def eliminarInstitucion(id: Int): Boolean = {
    ctx.transaction {
      // 1. Eliminar de Institucion
      val institucionesEliminadas = ctx.run(
        query[Institucion].filter(_.entidadId == lift(id)).delete
      )

      // 2. Eliminar de Entidad
      val entidadesEliminadas = ctx.run(
        query[Entidad].filter(_.id == lift(id)).delete
      )

      (institucionesEliminadas + entidadesEliminadas) > 0
    }
  }
}
