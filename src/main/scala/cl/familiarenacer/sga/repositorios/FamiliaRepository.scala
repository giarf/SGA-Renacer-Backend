package cl.familiarenacer.sga.repositorios

import cl.familiarenacer.sga.modelos.{Familia, Beneficiario, FamiliaMiembroDetalle}
import io.getquill._
import java.sql.DriverManager

/**
 * Repositorio para manejar la persistencia de Familias y sus miembros.
 *
 * @param ctx Contexto de Quill inyectado (generalmente DB.ctx).
 */
class FamiliaRepository(val ctx: PostgresJdbcContext[SnakeCase.type]) {
  import ctx._

  private def withConnection[A](fn: java.sql.Connection => A): A = {
    val host = sys.env.getOrElse("DATABASE_HOST", "localhost")
    val port = sys.env.getOrElse("DATABASE_PORT", "5432")
    val database = sys.env.getOrElse("DATABASE_NAME", "sga_renacer")
    val user = sys.env.getOrElse("DATABASE_USER", "postgres")
    val password = sys.env.getOrElse("DATABASE_PASSWORD", "")
    val conn = DriverManager.getConnection(s"jdbc:postgresql://$host:$port/$database", user, password)
    try fn(conn)
    finally conn.close()
  }

  def asegurarEsquemaFamilias(): Unit = withConnection { conn =>
    val statement = conn.createStatement()
    try {
      statement.execute("ALTER TABLE familia ADD COLUMN IF NOT EXISTS justificacion_vulnerabilidad TEXT")
      statement.execute(
        """
        CREATE TABLE IF NOT EXISTS familia_miembro (
          familia_id INTEGER NOT NULL REFERENCES familia(id) ON DELETE CASCADE,
          persona_id INTEGER NOT NULL REFERENCES persona_natural(entidad_id) ON DELETE CASCADE,
          rol_familiar TEXT,
          observaciones TEXT,
          created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
          PRIMARY KEY (familia_id, persona_id)
        )
        """
      )
      statement.execute(
        """
        INSERT INTO familia_miembro (familia_id, persona_id)
        SELECT familia_id, persona_id
        FROM beneficiario
        WHERE familia_id IS NOT NULL
        ON CONFLICT (familia_id, persona_id) DO NOTHING
        """
      )
    } finally statement.close()
  }

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
          _.jefeHogarId -> lift(familia.jefeHogarId),
          _.justificacionVulnerabilidad -> lift(familia.justificacionVulnerabilidad)
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

  def obtenerMiembrosDetalle(familiaId: Int): List[FamiliaMiembroDetalle] = withConnection { conn =>
    val sql =
      """
      SELECT e.id, p.entidad_id, p.nombres, p.apellidos, e.rut, e.correo, e.telefono, p.foto_url,
             fm.rol_familiar, fm.observaciones
      FROM familia_miembro fm
      JOIN persona_natural p ON p.entidad_id = fm.persona_id
      JOIN entidad e ON e.id = p.entidad_id
      WHERE fm.familia_id = ?
      ORDER BY CASE WHEN fm.rol_familiar = 'Jefe/a de hogar' THEN 0 ELSE 1 END, p.nombres, p.apellidos
      """
    val ps = conn.prepareStatement(sql)
    try {
      ps.setInt(1, familiaId)
      val rs = ps.executeQuery()
      val buffer = scala.collection.mutable.ListBuffer.empty[FamiliaMiembroDetalle]
      while (rs.next()) {
        buffer += FamiliaMiembroDetalle(
          id = rs.getInt("id"),
          personaId = rs.getInt("entidad_id"),
          nombres = rs.getString("nombres"),
          apellidos = Option(rs.getString("apellidos")),
          rut = Option(rs.getString("rut")),
          correo = Option(rs.getString("correo")),
          telefono = Option(rs.getString("telefono")),
          fotoUrl = Option(rs.getString("foto_url")),
          rolFamiliar = Option(rs.getString("rol_familiar")),
          observaciones = Option(rs.getString("observaciones"))
        )
      }
      buffer.toList
    } finally ps.close()
  }

  def agregarMiembroDetalle(familiaId: Int, personaId: Int, rolFamiliar: Option[String], observaciones: Option[String]): Long = withConnection { conn =>
    val ps = conn.prepareStatement(
      """
      INSERT INTO familia_miembro (familia_id, persona_id, rol_familiar, observaciones)
      VALUES (?, ?, ?, ?)
      ON CONFLICT (familia_id, persona_id)
      DO UPDATE SET rol_familiar = EXCLUDED.rol_familiar, observaciones = EXCLUDED.observaciones
      """
    )
    try {
      ps.setInt(1, familiaId)
      ps.setInt(2, personaId)
      rolFamiliar.fold(ps.setNull(3, java.sql.Types.VARCHAR))(ps.setString(3, _))
      observaciones.fold(ps.setNull(4, java.sql.Types.VARCHAR))(ps.setString(4, _))
      ps.executeUpdate().toLong
    } finally ps.close()
  }

  def actualizarMiembroDetalle(familiaId: Int, personaId: Int, rolFamiliar: Option[String], observaciones: Option[String]): Long = withConnection { conn =>
    val ps = conn.prepareStatement("UPDATE familia_miembro SET rol_familiar = ?, observaciones = ? WHERE familia_id = ? AND persona_id = ?")
    try {
      rolFamiliar.fold(ps.setNull(1, java.sql.Types.VARCHAR))(ps.setString(1, _))
      observaciones.fold(ps.setNull(2, java.sql.Types.VARCHAR))(ps.setString(2, _))
      ps.setInt(3, familiaId)
      ps.setInt(4, personaId)
      ps.executeUpdate().toLong
    } finally ps.close()
  }

  /**
   * Agrega un miembro (beneficiario) a una familia.
   * Actualiza el familiaId del beneficiario.
   */
  def agregarMiembro(familiaId: Int, personaId: Int): Long = {
    agregarMiembroDetalle(familiaId, personaId, None, None)
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
    withConnection { conn =>
      val ps = conn.prepareStatement("DELETE FROM familia_miembro WHERE familia_id = ? AND persona_id = ?")
      try {
        ps.setInt(1, familiaId)
        ps.setInt(2, personaId)
        ps.executeUpdate()
      } finally ps.close()
    }
    ctx.run(
      query[Beneficiario]
        .filter(b => b.personaId == lift(personaId) && b.familiaId.contains(lift(familiaId)))
        .update(_.familiaId -> lift(Option.empty[Int]))
    ) > 0
  }
}
