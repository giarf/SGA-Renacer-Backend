package cl.familiarenacer.sga.api.routes

import cl.familiarenacer.sga.api.ApiSupport
import cl.familiarenacer.sga.huellero.{Huellero, HuellaMatcher, PlantillaExtraida}
import cl.familiarenacer.sga.modelos._
import cl.familiarenacer.sga.modelos.HuellasJson._
import cl.familiarenacer.sga.repositorios.{AsistenciaRepository, HuellaRepository}
import play.api.libs.json._
import scala.util.control.NonFatal

class HuellasRoutes(repo: HuellaRepository, huellero: Huellero, matcher: HuellaMatcher, asistencia: AsistenciaRepository)
  (implicit cc: castor.Context, log: cask.Logger) extends cask.Routes with ApiSupport {

  private val TomasPorDedo = 3
  private val ReintentosRegistro = 2
  private val EsperaPorToma = 25

  private def safely(f: => cask.Response[String]): cask.Response[String] = try f catch {
    case e: HuellaError => respond(Json.obj("error" -> e.mensaje) ++ (if (e.codigo.isEmpty) Json.obj() else Json.obj("codigo" -> e.codigo)), e.status)
    case e: AsistenciaError => respond(Json.obj("error" -> e.mensaje), e.status)
    case _: JsResultException => respond(Json.obj("error" -> "Revisa los datos enviados."), 400)
    case _: com.fasterxml.jackson.core.JsonProcessingException => respond(Json.obj("error" -> "El contenido enviado no es válido."), 400)
    case e: java.sql.SQLException if e.getSQLState == "23503" => respond(Json.obj("error" -> "La persona o el evento ya no existe."), 404)
    case NonFatal(e) =>
      e.printStackTrace()
      respond(Json.obj("error" -> "No se pudo completar la operación con el huellero."), 500)
  }

  private def ms(desde: Long): Long = (System.nanoTime() - desde) / 1000000

  @cask.options("/api/huellero/estado")
  def estadoOptions() = corsOptions()
  @cask.options("/api/huellero/identificar")
  def identificarOptions() = corsOptions()
  @cask.options("/api/huellero/regenerar")
  def regenerarOptions() = corsOptions()
  @cask.options("/api/asistencia/eventos/:eventoId/huella")
  def asistenciaHuellaOptions(eventoId: Int) = corsOptions()
  @cask.options("/api/personas/:id/huellas")
  def huellasOptions(id: Int) = corsOptions()
  @cask.options("/api/personas/:id/huellas/:huellaId")
  def huellaOptions(id: Int, huellaId: Int) = corsOptions()

  /** Siempre 200: el frontend muestra el indicador verde o rojo según conectado. */
  @cask.get("/api/huellero/estado")
  def estado() = safely {
    val body = try Json.obj("conectado" -> true) ++ huellero.estado() catch {
      case e: HuellaError => Json.obj("conectado" -> false, "codigo" -> e.codigo, "error" -> e.mensaje)
    }
    val response = respond(body ++ Json.obj("huellasEnMemoria" -> matcher.total))
    response.copy(headers = response.headers ++ Seq("Cache-Control" -> "no-store"))
  }

  private case class Lectura(persona: PersonaIdentificada, puntaje: Double, imagen: ImagenHuella, extraccionMs: Long, comparacionMs: Long)

  /** Un toque: imagen de laka → plantilla → búsqueda 1:N en memoria. */
  private def leer(timeout: Option[Int]): Lectura = {
    if (matcher.vacio) throw HuellaError(404, "Aún no hay huellas registradas.", "sin_huellas")
    val imagen = huellero.imagen(timeout)
    val t0 = System.nanoTime()
    val probe = HuellaMatcher.extraer(imagen.png, imagen.dpi)
    val extraccionMs = ms(t0)
    if (probe.minucias < matcher.minimoMinucias)
      throw HuellaError(422, "La huella salió poco clara. Vuelve a poner el dedo, apoyándolo completo.", "calidad_baja")
    val t1 = System.nanoTime()
    val mejor = matcher.mejor(probe.template)
    val comparacionMs = ms(t1)
    val aceptada = mejor.filter(c => c.puntaje >= matcher.umbral && c.puntaje - c.segundaPersona >= matcher.margen)
    val persona = aceptada.flatMap { c =>
      val p = repo.persona(c.huellaId)
      if (p.isEmpty) matcher.quitar(c.huellaId) // la persona se eliminó: limpia el caché
      p.map(_ -> c.puntaje)
    }.getOrElse {
      println(f"huella no_coincide puntaje=${mejor.map(_.puntaje).getOrElse(0.0)}%.1f captura=${imagen.capturaMs} envio=${imagen.envioMs} extraccion=$extraccionMs comparacion=$comparacionMs")
      throw HuellaError(404, "La huella no coincide con ninguna persona registrada.", "no_coincide")
    }
    Lectura(persona._1, persona._2, imagen, extraccionMs, comparacionMs)
  }

  /** Pide al lector una huella y devuelve la persona. Acepta {timeout} opcional (2–120 s). */
  @cask.post("/api/huellero/identificar")
  def identificar(request: cask.Request) = safely {
    respond(Json.toJson(leer(HuellaValidacion.timeout(request.text())).persona))
  }

  /** Un toque registra la llegada: identifica y agrega a la persona al evento en la misma llamada. */
  @cask.post("/api/asistencia/eventos/:eventoId/huella")
  def asistenciaPorHuella(eventoId: Int, request: cask.Request) = safely {
    val inicio = System.nanoTime()
    val lectura = leer(HuellaValidacion.timeout(request.text()))
    val t = System.nanoTime()
    val (asistenciaId, creada) = asistencia.agregarPersona(eventoId, lectura.persona.id)
    val tiempos = TiemposHuella(lectura.imagen.capturaMs, lectura.imagen.envioMs, lectura.extraccionMs, lectura.comparacionMs, ms(t), ms(inicio))
    println(f"huella asistencia evento=$eventoId persona=${lectura.persona.id} puntaje=${lectura.puntaje}%.1f captura=${tiempos.capturaMs} " +
      s"envio=${tiempos.envioMs} extraccion=${tiempos.extraccionMs} comparacion=${tiempos.comparacionMs} registro=${tiempos.registroMs} total=${tiempos.totalMs}")
    respond(Json.toJson(AsistenciaPorHuella(lectura.persona, asistenciaId, creada, math.round(lectura.puntaje * 10) / 10.0, tiempos)), if (creada) 201 else 200)
  }

  @cask.get("/api/personas/:id/huellas")
  def listar(id: Int) = safely { respond(Json.obj("dedos" -> HuellaValidacion.dedos, "huellas" -> repo.listar(id))) }

  /**
   * Registra un dedo con 3 toques. Cada toma debe tener minucias suficientes y coincidir con la primera
   * (mismo dedo); si no, se repite solo esa toma. Rechaza un dedo que ya pertenece a otra persona.
   */
  @cask.post("/api/personas/:id/huellas")
  def registrar(id: Int, request: cask.Request) = safely {
    val dedo = HuellaValidacion.dedo(Json.parse(request.text()).as[RegistrarHuella].dedo)
    val (huellaId, nueva) = repo.reservar(id, dedo)
    try {
      var reintentos = 0
      val tomas = scala.collection.mutable.ArrayBuffer.empty[PlantillaExtraida]
      while (tomas.length < TomasPorDedo) {
        val n = tomas.length + 1
        val imagen = huellero.imagen(Some(EsperaPorToma), Some(huellaId), Some(n))
        val plantilla = HuellaMatcher.extraer(imagen.png, imagen.dpi)
        val problema =
          if (plantilla.minucias < matcher.minimoMinucias) Some("La huella salió poco clara.")
          else tomas.headOption.filter(primera => new com.machinezoo.sourceafis.FingerprintMatcher(primera.template).`match`(plantilla.template) < matcher.umbral)
            .map(_ => "Ese toque no coincide con el primero; usa el mismo dedo.")
        problema match {
          case Some(motivo) =>
            reintentos += 1
            if (reintentos > ReintentosRegistro)
              throw HuellaError(422, s"$motivo Limpia el lector y el dedo e intenta de nuevo.", "calidad_baja")
          case None =>
            if (n == 1) matcher.identificar(plantilla.template).filter(_.personaId != id).foreach { c =>
              val nombre = repo.persona(c.huellaId).map(_.nombreCompleto).getOrElse("otra persona")
              throw HuellaError(409, s"Esa huella ya está registrada a nombre de $nombre.", "duplicada")
            }
            tomas += plantilla
        }
      }
      val guardadas = tomas.zipWithIndex.map { case (p, i) => (i + 1, p.bytes, p.minucias) }.toSeq
      val huella = repo.guardarTomas(huellaId, guardadas)
      matcher.reemplazar(huellaId, guardadas.map { case (n, bytes, minucias) => TomaGuardada(huellaId, id, n, bytes, minucias) })
      respond(Json.toJson(huella), 201)
    } catch {
      case e: Throwable =>
        if (nueva) { repo.descartarSinTomas(huellaId); scala.util.Try(huellero.borrar(huellaId)) }
        throw e
    }
  }

  @cask.delete("/api/personas/:id/huellas/:huellaId")
  def eliminar(id: Int, huellaId: Int) = safely {
    repo.eliminar(id, huellaId)
    matcher.quitar(huellaId)
    // Las imágenes en laka se borran sin bloquear la respuesta si el lector no está disponible.
    scala.util.Try(huellero.borrar(huellaId)).failed.foreach(e => println(s"huella $huellaId: no se pudieron borrar las imágenes en laka (${e.getMessage})"))
    respond(Json.obj("mensaje" -> "Huella eliminada."))
  }

  /** Recrea todas las plantillas desde las imágenes archivadas en laka (al cambiar de versión de SourceAFIS). */
  @cask.post("/api/huellero/regenerar")
  def regenerar() = safely {
    val resultado = repo.huellas().map { case (huellaId, personaId) =>
      val tomas = huellero.tomas(huellaId).flatMap { n =>
        scala.util.Try(HuellaMatcher.extraer(huellero.toma(huellaId, n), 500)).toOption.map(p => (n, p.bytes, p.minucias))
      }
      if (tomas.nonEmpty) {
        repo.guardarTomas(huellaId, tomas)
        matcher.reemplazar(huellaId, tomas.map { case (n, b, m) => TomaGuardada(huellaId, personaId, n, b, m) })
      }
      huellaId -> tomas.length
    }
    respond(Json.obj("regeneradas" -> resultado.count(_._2 > 0), "sinImagenes" -> resultado.filter(_._2 == 0).map(_._1)))
  }

  initialize()
}
