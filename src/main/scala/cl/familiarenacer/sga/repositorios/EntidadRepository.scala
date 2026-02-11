package cl.familiarenacer.sga.repositorios

import cl.familiarenacer.sga.modelos.{Entidad, EntidadResumen, Institucion, PersonaNatural}
import io.getquill._

/**
 * Repositorio para manejar la persistencia de Entidades y Personas.
 * Encapsula la lógica de acceso a datos utilizando Quill.
 *
 * @param ctx Contexto de Quill inyectado (generalmente DB.ctx).
 */
class EntidadRepository(val ctx: PostgresJdbcContext[SnakeCase.type]) {
  import ctx._

  /**
   * Registra una nueva Persona Natural en el sistema de forma transaccional.
   * La operación es atómica: primero crea la Entidad base y luego la PersonaNatural asociada.
   * Si falla cualquiera de los pasos, no se guarda nada en la base de datos.
   *
   * @param persona Datos de la persona a registrar (sin ID todavía).
   * @param entidad Datos base de la entidad (contacto, dirección, etc.).
   * @return El ID generado para la nueva entidad/persona.
   */
  def registrarPersonaNatural(persona: PersonaNatural, entidad: Entidad): Long = {
    ctx.transaction {
      // 1. Insertamos la Entidad y recuperamos el ID generado autoincrementalmente.
      val entidadId = ctx.run(
        query[Entidad]
          .insertValue(lift(entidad))
          .returningGenerated(_.id)
      )

      // 2. Asociamos el ID generado a la PersonaNatural.
      val personaConId = persona.copy(entidadId = entidadId)

      // 3. Insertamos la PersonaNatural.
      ctx.run(
        query[PersonaNatural].insertValue(lift(personaConId))
      )

      // Retornamos el ID generado.
      entidadId.toLong
    }
  }

  /**
   * Busca una Persona Natural por su RUT.
   * Realiza un JOIN implícito entre PersonaNatural y Entidad para filtrar por el RUT de la entidad base.
   *
   * @param rut El RUT a buscar.
   * @return Un Option con la PersonaNatural si es encontrada, o None.
   */
  def buscarPorRut(rut: String): Option[PersonaNatural] = {
    ctx.run(
      for {
        p <- query[PersonaNatural]
        e <- query[Entidad] if p.entidadId == e.id && e.rut == lift(Option(rut))
      } yield p
    ).headOption
  }

  /**
   * Verifica si existe una entidad con el RUT dado.
   */
  def existeRut(rut: String): Boolean = {
    ctx.run(query[Entidad].filter(_.rut == lift(Option(rut))).map(_.id)).nonEmpty
  }

  /**
   * Actualiza una Persona Natural y su Entidad asociada de forma transaccional.
   * La operación es atómica: actualiza tanto la Entidad base como la PersonaNatural.
   * Si falla cualquiera de los pasos, ningún cambio se guarda en la base de datos.
   *
   * @param entidadId ID de la entidad a actualizar.
   * @param persona Datos actualizados de la persona.
   * @param entidad Datos actualizados de la entidad base.
   * @return El número de filas actualizadas.
   */
  def actualizarPersonaNatural(entidadId: Int, persona: PersonaNatural, entidad: Entidad): Long = {
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
            _.comuna -> lift(entidad.comuna)
            // No actualizamos createdAt
          )
      )

      // 2. Actualizar la PersonaNatural
      val personaRows = ctx.run(
        query[PersonaNatural]
          .filter(_.entidadId == lift(entidadId))
          .update(
            _.nombres -> lift(persona.nombres),
            _.apellidos -> lift(persona.apellidos),
            _.genero -> lift(persona.genero)
          )
      )

      // Retornar el número de filas actualizadas (debería ser 2 si todo fue bien)
      (entidadRows + personaRows).toLong
    }
  }

  /**
   * Obtiene los datos completos de una Persona Natural (Entidad + PersonaNatural).
   *
   * @param entidadId ID de la entidad/persona a obtener.
   * @return Option con tupla (Entidad, PersonaNatural) si existe, None si no.
   */
  def obtenerPersonaCompleta(entidadId: Int): Option[(Entidad, PersonaNatural)] = {
    ctx.run(
      query[Entidad]
        .filter(_.id == lift(entidadId))
        .join(query[PersonaNatural]).on((e, p) => e.id == p.entidadId)
    ).headOption
  }

  /**
   * Lista todas las Personas Naturales con sus datos de Entidad.
   *
   * @return Lista de tuplas (Entidad, PersonaNatural).
   */
  def listarTodasPersonas(): List[(Entidad, PersonaNatural)] = {
    ctx.run(
      query[Entidad]
        .filter(_.tipoEntidad.contains("Persona"))
        .join(query[PersonaNatural]).on((e, p) => e.id == p.entidadId)
    )
  }

  /**
   * Lista todas las personas naturales registradas en el sistema.
   * Mantenemos este método por compatibilidad, aunque se recomienda usar listarEntidadesUnificadas.
   */
  def listarPersonas(tipoFiltro: Option[String] = None): List[PersonaNatural] = {
    val q = quote {
      query[PersonaNatural]
        .join(query[Entidad]).on(_.entidadId == _.id)
        .filter { case (p, e) =>
           lift(tipoFiltro).forall(tf => e.tipoEntidad.contains(tf))
        }
        .map(_._1) // Solo nos interesan los datos de la persona
    }
    ctx.run(q)
  }

  /**
   * Lista unificada de entidades (Personas e Instituciones) proyectada en un DTO resumen.
   * Realiza LEFT JOINs para obtener datos específicos de cada subtipo si existen.
   *
   * Lógica del Join:
   * - Base: Entidad (e)
   * - Left Join PersonaNatural (p) ON e.id = p.entidad_id
   * - Left Join Institucion (i) ON e.id = i.entidad_id
   *
   * Construcción de campos (proyección):
   * - Nombre Completo: Intenta usar (Nombre + Apellido) de Persona; si es nulo, usa Razón Social de Institución.
   * 
   * @param tipoFiltro Filtra por tipo de entidad (Persona, Institucion, etc.)
   * @param queryBusqueda Busca por nombre (con unaccent) o RUT parcial - ej: "ju" encuentra "Juan"
   */
  def listarEntidadesUnificadas(tipoFiltro: Option[String] = None, queryBusqueda: Option[String] = None): List[EntidadResumen] = {
    queryBusqueda match {
      case Some(termino) if termino.trim.nonEmpty =>
        // Búsqueda con unaccent y LIKE en SQL
        val terminoLike = s"%${termino.trim.toLowerCase}%"
        
        val terminoStartWith = s"${termino.trim.toLowerCase}%"
        
        val q = quote {
          query[Entidad]
            .leftJoin(query[PersonaNatural]).on(_.id == _.entidadId)
            .leftJoin(query[Institucion]).on(_._1.id == _.entidadId)
            .filter { case ((e, p), i) =>
              // Filtro de tipo
              lift(tipoFiltro).forall(tf => e.tipoEntidad.contains(tf)) &&
              // Filtro de búsqueda con concatenación de nombres completos
              (
                // Búsqueda en nombres concatenados de PersonaNatural (nombres + ' ' + apellidos)
                (p.isDefined && (
                  infix"unaccent(lower(coalesce(${p.map(_.nombres)}, '') || ' ' || coalesce(${p.flatMap(_.apellidos)}, '')))".as[String] like lift(terminoLike)
                )) ||
                // Búsqueda en razonSocial de Institucion
                (i.isDefined && (
                  infix"unaccent(lower(${i.map(_.razonSocial)}))".as[Option[String]].exists(r => r like lift(terminoLike))
                )) ||
                // Búsqueda en RUT
                infix"lower(${e.rut})".as[Option[String]].exists(r => r like lift(terminoLike))
              )
            }
            .sortBy { case ((e, p), i) =>
              // Ranking de Relevancia: Priorizamos coincidencias al inicio
              infix"""
                CASE 
                  WHEN unaccent(lower(coalesce(${p.map(_.nombres)}, ''))) LIKE ${lift(terminoStartWith)} THEN 1
                  WHEN unaccent(lower(coalesce(${p.flatMap(_.apellidos)}, ''))) LIKE ${lift(terminoStartWith)} THEN 2
                  WHEN unaccent(lower(${i.map(_.razonSocial)})) LIKE ${lift(terminoStartWith)} THEN 2
                  WHEN lower(${e.rut}) LIKE ${lift(terminoStartWith)} THEN 3
                  ELSE 4
                END
              """.as[Int]
            }
            .map { case ((e, p), i) =>
              (
                e.id,
                e.rut.getOrElse("Sin RUT"),
                p.map(x => x.nombres + " " + x.apellidos.getOrElse("")).getOrElse(
                  i.map(_.razonSocial).getOrElse("Sin Nombre")
                ),
                e.tipoEntidad.getOrElse("Desconocido"),
                e.correo,
                e.telefono,
                e.direccion,
                e.comuna,
                p.map(_.genero) // Option[Option[String]]
              )
            }
        }
        ctx.run(q).map { case (id, ident, nombre, tipo, correo, tel, dir, com, genOpt) =>
          EntidadResumen(id, ident, nombre, tipo, correo, tel, dir, com, genOpt.flatten)
        }
        
      case _ =>
        // Sin búsqueda, solo filtro de tipo
        val q = quote {
          query[Entidad]
            .leftJoin(query[PersonaNatural]).on(_.id == _.entidadId)
            .leftJoin(query[Institucion]).on(_._1.id == _.entidadId)
            .filter { case ((e, p), i) =>
              lift(tipoFiltro).forall(tf => e.tipoEntidad.contains(tf))
            }
            .map { case ((e, p), i) =>
              (
                e.id,
                e.rut.getOrElse("Sin RUT"),
                p.map(x => x.nombres + " " + x.apellidos.getOrElse("")).getOrElse(
                  i.map(_.razonSocial).getOrElse("Sin Nombre")
                ),
                e.tipoEntidad.getOrElse("Desconocido"),
                e.correo,
                e.telefono,
                e.direccion,
                e.comuna,
                p.map(_.genero) // Option[Option[String]]
              )
            }
        }
        ctx.run(q).map { case (id, ident, nombre, tipo, correo, tel, dir, com, genOpt) =>
          EntidadResumen(id, ident, nombre, tipo, correo, tel, dir, com, genOpt.flatten)
        }
    }
  }

  /**
   * Actualiza los datos de una Persona Natural y su Entidad base.
   * La operación es transaccional: si falla una actualización, se hace rollback de todo.
   * Se utiliza updateValue para reemplazar el registro completo, por lo que se deben proveer todos los datos.
   *
   * @param entidad Objeto Entidad con los datos actualizados (debe tener el ID correcto).
   * @param persona Objeto PersonaNatural con los datos actualizados.
   */
  def actualizarPersona(entidad: Entidad, persona: PersonaNatural): Unit = {
    ctx.transaction {
      // 1. Actualizar tabla Entidad
      ctx.run(
        query[Entidad]
          .filter(_.id == lift(entidad.id))
          .updateValue(lift(entidad))
      )

      // 2. Actualizar tabla PersonaNatural
      ctx.run(
        query[PersonaNatural]
          .filter(_.entidadId == lift(persona.entidadId))
          .updateValue(lift(persona))
      )
    }
  }

  /**
   * Elimina una Persona Natural y su Entidad base de forma transaccional.
   * Primero elimina la PersonaNatural y luego la Entidad.
   *
   * @param id ID de la entidad/persona a eliminar.
   * @return true si se eliminó correctamente, false si no existía.
   */
  def eliminarPersona(id: Int): Boolean = {
    ctx.transaction {
      // 1. Eliminar de PersonaNatural
      val personasEliminadas = ctx.run(
        query[PersonaNatural].filter(_.entidadId == lift(id)).delete
      )

      // 2. Eliminar de Entidad
      val entidadesEliminadas = ctx.run(
        query[Entidad].filter(_.id == lift(id)).delete
      )

      (personasEliminadas + entidadesEliminadas) > 0
    }
  }
}
