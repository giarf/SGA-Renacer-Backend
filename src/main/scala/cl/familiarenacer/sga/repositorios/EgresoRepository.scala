package cl.familiarenacer.sga.repositorios

import cl.familiarenacer.sga.modelos.{EgresoRecurso, EgresoAyudaSocial, EgresoConsumoInterno, DetalleEgresoRecurso}
import io.getquill._

/**
 * Repositorio para manejar la persistencia de Egresos.
 *
 * @param ctx Contexto de Quill inyectado (generalmente DB.ctx).
 */
class EgresoRepository(val ctx: PostgresJdbcContext[SnakeCase.type]) {
  import ctx._

  /**
   * Registra un egreso de tipo Ayuda Social de forma transaccional.
   * @return El ID del egreso creado.
   */
  def registrarAyudaSocial(
    egreso: EgresoRecurso,
    ayuda: EgresoAyudaSocial,
    detalles: List[DetalleEgresoRecurso]
  ): Long = {
    ctx.transaction {
      // 1. Insertar el egreso base
      val egresoId = ctx.run(
        query[EgresoRecurso]
          .insertValue(lift(egreso))
          .returningGenerated(_.id)
      )

      // 2. Insertar el detalle de ayuda social
      val ayudaConId = ayuda.copy(egresoId = Some(egresoId))
      ctx.run(
        query[EgresoAyudaSocial].insertValue(lift(ayudaConId))
      )

      // 3. Insertar los detalles de los recursos egresados
      if (detalles.nonEmpty) {
        detalles.foreach { detalle =>
          ctx.run(
            query[DetalleEgresoRecurso].insert(
              _.egresoId          -> lift(Option(egresoId)),
              _.itemCatalogoId    -> lift(detalle.itemCatalogoId),
              _.cantidad          -> lift(detalle.cantidad),
              _.precioUnitarioPpp -> lift(detalle.precioUnitarioPpp)
            )
          )
        }
      }

      egresoId.toLong
    }
  }

  /**
   * Registra un egreso de tipo Consumo Interno de forma transaccional.
   * @return El ID del egreso creado.
   */
  def registrarConsumoInterno(
    egreso: EgresoRecurso,
    consumo: EgresoConsumoInterno,
    detalles: List[DetalleEgresoRecurso]
  ): Long = {
    ctx.transaction {
      // 1. Insertar el egreso base
      val egresoId = ctx.run(
        query[EgresoRecurso]
          .insertValue(lift(egreso))
          .returningGenerated(_.id)
      )

      // 2. Insertar el detalle de consumo interno
      val consumoConId = consumo.copy(egresoId = Some(egresoId))
      ctx.run(
        query[EgresoConsumoInterno].insertValue(lift(consumoConId))
      )

      // 3. Insertar los detalles de los recursos egresados
      if (detalles.nonEmpty) {
        detalles.foreach { detalle =>
          ctx.run(
            query[DetalleEgresoRecurso].insert(
              _.egresoId          -> lift(Option(egresoId)),
              _.itemCatalogoId    -> lift(detalle.itemCatalogoId),
              _.cantidad          -> lift(detalle.cantidad),
              _.precioUnitarioPpp -> lift(detalle.precioUnitarioPpp)
            )
          )
        }
      }

      egresoId.toLong
    }
  }

  /**
   * Lista todos los egresos.
   */
  def listarEgresos(): List[EgresoRecurso] = {
    ctx.run(query[EgresoRecurso])
  }

  /**
   * Obtiene un egreso por su ID.
   */
  def obtenerEgreso(id: Int): Option[EgresoRecurso] = {
    ctx.run(
      query[EgresoRecurso].filter(_.id == lift(id))
    ).headOption
  }
}
