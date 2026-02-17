package cl.familiarenacer.sga.repositorios

import cl.familiarenacer.sga.modelos.{Familia, Beneficiario}
import io.getquill._

/**
 * Repositorio para manejar la persistencia de Familias y sus miembros.
 *
 * @param ctx Contexto de Quill inyectado (generalmente DB.ctx).
 */
class FamiliaRepository(val ctx: PostgresJdbcContext[SnakeCase.type]) {
  import ctx._

  /**
   * Lista todas las familias.
   */
  def listarFamilias(): List[Familia] = {
    ctx.run(query[Familia])
  }

  /**
   * Crea una nueva familia.
   * @return El ID generado para la nueva familia.
   */
  def crearFamilia(familia: Familia): Long = {
    ctx.run(
      query[Familia]
        .insertValue(lift(familia))
        .returningGenerated(_.id)
    ).toLong
  }

  /**
   * Obtiene una familia por su ID.
   */
  def obtenerFamilia(id: Int): Option[Familia] = {
    ctx.run(
      query[Familia].filter(_.id == lift(id))
    ).headOption
  }

  /**
   * Actualiza los datos de una familia.
   */
  def actualizarFamilia(id: Int, familia: Familia): Long = {
    ctx.run(
      query[Familia]
        .filter(_.id == lift(id))
        .update(
          _.nombreFamilia -> lift(familia.nombreFamilia),
          _.puntosVulnerabilidad -> lift(familia.puntosVulnerabilidad),
          _.jefeHogarId -> lift(familia.jefeHogarId)
        )
    )
  }

  /**
   * Elimina una familia.
   */
  def eliminarFamilia(id: Int): Boolean = {
    ctx.run(
      query[Familia].filter(_.id == lift(id)).delete
    ) > 0
  }

  /**
   * Obtiene todos los beneficiarios que pertenecen a una familia.
   */
  def obtenerMiembrosFamilia(familiaId: Int): List[Beneficiario] = {
    ctx.run(
      query[Beneficiario].filter(_.familiaId.contains(lift(familiaId)))
    )
  }

  /**
   * Agrega un miembro (beneficiario) a una familia.
   * Actualiza el familiaId del beneficiario.
   */
  def agregarMiembro(familiaId: Int, personaId: Int): Long = {
    ctx.run(
      query[Beneficiario]
        .filter(_.personaId == lift(personaId))
        .update(_.familiaId -> lift(Option(familiaId)))
    )
  }

  /**
   * Quita un miembro de una familia.
   * Establece el familiaId del beneficiario como None.
   */
  def quitarMiembro(familiaId: Int, personaId: Int): Boolean = {
    ctx.run(
      query[Beneficiario]
        .filter(b => b.personaId == lift(personaId) && b.familiaId.contains(lift(familiaId)))
        .update(_.familiaId -> lift(Option.empty[Int]))
    ) > 0
  }
}
