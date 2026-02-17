package cl.familiarenacer.sga.repositorios

import cl.familiarenacer.sga.modelos.{Beneficiario, Colaborador, Trabajador, Directivo, PersonaNatural, Entidad}
import io.getquill._

/**
 * Repositorio para manejar la persistencia de los roles de Persona.
 * Gestiona Beneficiario, Colaborador, Trabajador y Directivo.
 *
 * @param ctx Contexto de Quill inyectado (generalmente DB.ctx).
 */
class RolesRepository(val ctx: PostgresJdbcContext[SnakeCase.type]) {
  import ctx._

  // ===== LISTADOS INDEPENDIENTES (con datos de persona) =====

  /**
   * Lista todos los beneficiarios con datos de persona y entidad.
   */
  def listarBeneficiarios(): List[(Entidad, PersonaNatural, Beneficiario)] = {
    ctx.run(
      for {
        b <- query[Beneficiario]
        p <- query[PersonaNatural].filter(_.entidadId == b.personaId)
        e <- query[Entidad].filter(_.id == p.entidadId)
      } yield (e, p, b)
    )
  }

  /**
   * Lista todos los colaboradores con datos de persona y entidad.
   */
  def listarColaboradores(): List[(Entidad, PersonaNatural, Colaborador)] = {
    ctx.run(
      for {
        c <- query[Colaborador]
        p <- query[PersonaNatural].filter(_.entidadId == c.personaId)
        e <- query[Entidad].filter(_.id == p.entidadId)
      } yield (e, p, c)
    )
  }

  /**
   * Lista todos los trabajadores con datos de persona y entidad.
   */
  def listarTrabajadores(): List[(Entidad, PersonaNatural, Trabajador)] = {
    ctx.run(
      for {
        t <- query[Trabajador]
        p <- query[PersonaNatural].filter(_.entidadId == t.personaId)
        e <- query[Entidad].filter(_.id == p.entidadId)
      } yield (e, p, t)
    )
  }

  /**
   * Lista todos los directivos con datos de persona y entidad.
   */
  def listarDirectivos(): List[(Entidad, PersonaNatural, Directivo)] = {
    ctx.run(
      for {
        d <- query[Directivo]
        p <- query[PersonaNatural].filter(_.entidadId == d.personaId)
        e <- query[Entidad].filter(_.id == p.entidadId)
      } yield (e, p, d)
    )
  }
  // ===== BENEFICIARIO =====

  /**
   * Obtiene la información de beneficiario de una persona.
   */
  def obtenerBeneficiario(personaId: Int): Option[Beneficiario] = {
    ctx.run(
      query[Beneficiario].filter(_.personaId == lift(personaId))
    ).headOption
  }

  /**
   * Asigna el rol de beneficiario a una persona.
   */
  def asignarBeneficiario(beneficiario: Beneficiario): Long = {
    ctx.run(
      query[Beneficiario].insertValue(lift(beneficiario))
    )
  }

  /**
   * Actualiza la información de beneficiario.
   */
  def actualizarBeneficiario(personaId: Int, beneficiario: Beneficiario): Long = {
    ctx.run(
      query[Beneficiario]
        .filter(_.personaId == lift(personaId))
        .update(
          _.familiaId -> lift(beneficiario.familiaId),
          _.escolaridad -> lift(beneficiario.escolaridad),
          _.tallaRopa -> lift(beneficiario.tallaRopa),
          _.observacionesMedicas -> lift(beneficiario.observacionesMedicas)
        )
    )
  }

  /**
   * Quita el rol de beneficiario de una persona.
   */
  def quitarBeneficiario(personaId: Int): Boolean = {
    ctx.run(
      query[Beneficiario].filter(_.personaId == lift(personaId)).delete
    ) > 0
  }

  // ===== COLABORADOR =====

  /**
   * Obtiene la información de colaborador de una persona.
   */
  def obtenerColaborador(personaId: Int): Option[Colaborador] = {
    ctx.run(
      query[Colaborador].filter(_.personaId == lift(personaId))
    ).headOption
  }

  /**
   * Asigna el rol de colaborador a una persona.
   */
  def asignarColaborador(colaborador: Colaborador): Long = {
    ctx.run(
      query[Colaborador].insertValue(lift(colaborador))
    )
  }

  /**
   * Actualiza la información de colaborador.
   */
  def actualizarColaborador(personaId: Int, colaborador: Colaborador): Long = {
    ctx.run(
      query[Colaborador]
        .filter(_.personaId == lift(personaId))
        .update(
          _.tipoColaborador -> lift(colaborador.tipoColaborador),
          _.esAnonimo -> lift(colaborador.esAnonimo)
        )
    )
  }

  /**
   * Quita el rol de colaborador de una persona.
   */
  def quitarColaborador(personaId: Int): Boolean = {
    ctx.run(
      query[Colaborador].filter(_.personaId == lift(personaId)).delete
    ) > 0
  }

  // ===== TRABAJADOR =====

  /**
   * Obtiene la información de trabajador de una persona.
   */
  def obtenerTrabajador(personaId: Int): Option[Trabajador] = {
    ctx.run(
      query[Trabajador].filter(_.personaId == lift(personaId))
    ).headOption
  }

  /**
   * Asigna el rol de trabajador a una persona.
   */
  def asignarTrabajador(trabajador: Trabajador): Long = {
    ctx.run(
      query[Trabajador].insertValue(lift(trabajador))
    )
  }

  /**
   * Actualiza la información de trabajador.
   */
  def actualizarTrabajador(personaId: Int, trabajador: Trabajador): Long = {
    ctx.run(
      query[Trabajador]
        .filter(_.personaId == lift(personaId))
        .update(
          _.cargo -> lift(trabajador.cargo),
          _.fechaIngreso -> lift(trabajador.fechaIngreso)
        )
    )
  }

  /**
   * Quita el rol de trabajador de una persona.
   */
  def quitarTrabajador(personaId: Int): Boolean = {
    ctx.run(
      query[Trabajador].filter(_.personaId == lift(personaId)).delete
    ) > 0
  }

  // ===== DIRECTIVO =====

  /**
   * Obtiene la información de directivo de una persona.
   */
  def obtenerDirectivo(personaId: Int): Option[Directivo] = {
    ctx.run(
      query[Directivo].filter(_.personaId == lift(personaId))
    ).headOption
  }

  /**
   * Asigna el rol de directivo a una persona.
   */
  def asignarDirectivo(directivo: Directivo): Long = {
    ctx.run(
      query[Directivo].insertValue(lift(directivo))
    )
  }

  /**
   * Actualiza la información de directivo.
   */
  def actualizarDirectivo(personaId: Int, directivo: Directivo): Long = {
    ctx.run(
      query[Directivo]
        .filter(_.personaId == lift(personaId))
        .update(
          _.cargo -> lift(directivo.cargo),
          _.firmaDigitalUrl -> lift(directivo.firmaDigitalUrl)
        )
    )
  }

  /**
   * Quita el rol de directivo de una persona.
   */
  def quitarDirectivo(personaId: Int): Boolean = {
    ctx.run(
      query[Directivo].filter(_.personaId == lift(personaId)).delete
    ) > 0
  }
}
