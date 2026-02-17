package cl.familiarenacer.sga.repositorios

import cl.familiarenacer.sga.modelos.{CuentaFinanciera, IngresoRecurso, IngresoCompra, IngresoPecuniario, EgresoRecurso}
import io.getquill._
import java.time.LocalDate

/**
 * Repositorio para la gestión de Cuentas Financieras (cajas).
 * Maneja CRUD de cuentas y consulta de movimientos asociados.
 */
class CuentaFinancieraRepository(val ctx: PostgresJdbcContext[SnakeCase.type]) {
  import ctx._

  /**
   * Lista todas las cuentas financieras.
   */
  def listarCuentas(): List[CuentaFinanciera] = {
    ctx.run(query[CuentaFinanciera])
  }

  /**
   * Crea una nueva cuenta financiera.
   * @return El ID generado de la nueva cuenta.
   */
  def crearCuenta(cuenta: CuentaFinanciera): Int = {
    ctx.run(
      query[CuentaFinanciera]
        .insert(
          _.nombre -> lift(cuenta.nombre),
          _.saldoActual -> lift(Option(BigDecimal(0)))
        )
        .returningGenerated(_.id)
    ).toInt
  }

  /**
   * Obtiene una cuenta financiera por ID.
   */
  def obtenerCuenta(id: Int): Option[CuentaFinanciera] = {
    ctx.run(
      query[CuentaFinanciera].filter(_.id == lift(id))
    ).headOption
  }

  /**
   * Actualiza el nombre de una cuenta financiera.
   */
  def actualizarCuenta(id: Int, nombre: String): Long = {
    ctx.run(
      query[CuentaFinanciera]
        .filter(_.id == lift(id))
        .update(_.nombre -> lift(Option(nombre)))
    )
  }

  /**
   * Elimina una cuenta financiera solo si no tiene movimientos asociados.
   * Las FK constraints de la BD evitarán la eliminación si hay movimientos.
   */
  def eliminarCuenta(id: Int): Boolean = {
    ctx.run(
      query[CuentaFinanciera].filter(_.id == lift(id)).delete
    ) > 0
  }

  /**
   * Obtiene todos los movimientos (ingresos pecuniarios y compras) de una cuenta.
   * Retorna una tupla de:
   * - Ingresos pecuniarios que llegaron a esta cuenta (donaciones, subvenciones, etc.)
   * - Compras que salieron de esta cuenta
   */
  def obtenerMovimientos(cuentaId: Int): (List[IngresoRecurso], List[IngresoRecurso]) = {
    // Ingresos: pecuniarios donde cuentaDestinoId = cuentaId
    val ingresos = ctx.run(
      for {
        pec <- query[IngresoPecuniario].filter(_.cuentaDestinoId == lift(Option(cuentaId)))
        ing <- query[IngresoRecurso].filter(_.id == pec.ingresoId)
      } yield ing
    )

    // Egresos (compras donde cuentaOrigenId = cuentaId)
    val egresos = ctx.run(
      for {
        compra <- query[IngresoCompra].filter(_.cuentaOrigenId == lift(Option(cuentaId)))
        ing <- query[IngresoRecurso].filter(_.id == compra.ingresoId)
      } yield ing
    )

    (ingresos, egresos)
  }
}
