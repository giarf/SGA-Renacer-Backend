package cl.familiarenacer.sga.repositorios

import io.getquill._
import play.api.libs.json.{JsValue, Json, OFormat}

import java.time.LocalDateTime
import scala.util.Using

/**
 * Registro de quién hizo qué. Los cambios en Authentik los ejecuta la cuenta de servicio
 * `sga-backend`, así que la persona real (sacada de su token) queda aquí.
 * Nunca se guardan contraseñas.
 */
case class Auditoria(
  id: Long = 0,
  fecha: LocalDateTime,
  actorSub: String,
  actorNombre: String,
  actorEmail: Option[String],
  accion: String,
  objetivoTipo: String,
  objetivoId: Option[String],
  objetivoNombre: Option[String],
  detalle: Option[String]
)
object Auditoria {
  implicit val format: OFormat[Auditoria] = Json.format[Auditoria]
}

class AuditoriaRepository(val ctx: PostgresJdbcContext[SnakeCase.type]) {
  import ctx._

  def asegurarEsquema(): Unit =
    Using.resource(ctx.dataSource.getConnection) { conn =>
      Using.resource(conn.createStatement())(_.execute(
        """CREATE TABLE IF NOT EXISTS auditoria (
          |  id BIGSERIAL PRIMARY KEY,
          |  fecha TIMESTAMP NOT NULL DEFAULT now(),
          |  actor_sub TEXT NOT NULL,
          |  actor_nombre TEXT NOT NULL,
          |  actor_email TEXT,
          |  accion TEXT NOT NULL,
          |  objetivo_tipo TEXT NOT NULL,
          |  objetivo_id TEXT,
          |  objetivo_nombre TEXT,
          |  detalle TEXT
          |);
          |CREATE INDEX IF NOT EXISTS auditoria_objetivo_idx ON auditoria (objetivo_tipo, objetivo_id);
          |CREATE INDEX IF NOT EXISTS auditoria_fecha_idx ON auditoria (fecha DESC);""".stripMargin
      ))
    }

  def registrar(
    actorSub: String,
    actorNombre: String,
    actorEmail: Option[String],
    accion: String,
    objetivoTipo: String,
    objetivoId: Option[String],
    objetivoNombre: Option[String],
    detalle: Option[JsValue] = None
  ): Unit = {
    val fila = Auditoria(
      fecha = LocalDateTime.now(),
      actorSub = actorSub,
      actorNombre = actorNombre,
      actorEmail = actorEmail.filter(_.nonEmpty),
      accion = accion,
      objetivoTipo = objetivoTipo,
      objetivoId = objetivoId,
      objetivoNombre = objetivoNombre,
      detalle = detalle.map(_.toString)
    )
    ctx.run(query[Auditoria].insertValue(lift(fila)).returningGenerated(_.id))
    ()
  }

  def listar(objetivoTipo: Option[String], limite: Int): List[Auditoria] = {
    val tope = math.max(1, math.min(limite, 500))
    objetivoTipo match {
      case Some(tipo) => ctx.run(query[Auditoria].filter(_.objetivoTipo == lift(tipo)).sortBy(_.fecha)(Ord.desc).take(lift(tope)))
      case None => ctx.run(query[Auditoria].sortBy(_.fecha)(Ord.desc).take(lift(tope)))
    }
  }
}
