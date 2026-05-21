package cl.familiarenacer.sga.api.routes

import cl.familiarenacer.sga.api.ApiSupport
import cl.familiarenacer.sga.modelos._
import cl.familiarenacer.sga.repositorios.{EntidadRepository, EtiquetaRepository}
import io.undertow.server.handlers.form.{FormData, FormDataParser, FormParserFactory}
import play.api.libs.json._
import java.time.LocalDate
import java.nio.file.{Files, Path, Paths, StandardCopyOption}
import scala.jdk.CollectionConverters._

class PersonasRoutes(entidadRepo: EntidadRepository, etiquetaRepo: EtiquetaRepository)(implicit cc: castor.Context, log: cask.Logger) extends cask.Routes with ApiSupport {

  // ===== DTOs =====

  case class EditarPersonaRequest(
    id: Int,
    rut: Option[String],
    tipoEntidad: String,
    telefono: Option[String],
    correo: Option[String],
    direccion: Option[String],
    comuna: Option[String],
    redSocial: Option[String] = None,
    gestorId: Option[Int] = None,
    anotaciones: Option[String] = None,
    sector: Option[String] = None,
    nombres: String,
    apellidos: Option[String],
    genero: Option[String],
    ocupacion: Option[String] = None,
    fechaNacimiento: Option[LocalDate] = None,
    fotoUrl: Option[String] = None
  )
  implicit val editarPersonaFormat: OFormat[EditarPersonaRequest] = Json.format[EditarPersonaRequest]

  case class PersonaCompletaResponse(
    id: Int,
    rut: Option[String],
    tipoEntidad: Option[String],
    telefono: Option[String],
    correo: Option[String],
    direccion: Option[String],
    comuna: Option[String],
    redSocial: Option[String] = None,
    gestorId: Option[Int] = None,
    anotaciones: Option[String] = None,
    sector: Option[String] = None,
    nombres: String,
    apellidos: Option[String],
    genero: Option[String],
    ocupacion: Option[String] = None,
    fechaNacimiento: Option[LocalDate] = None,
    fotoUrl: Option[String] = None,
    etiquetas: List[Etiqueta] = Nil
  )
  implicit val personaCompletaFormat: OFormat[PersonaCompletaResponse] = Json.format[PersonaCompletaResponse]

  case class RegistrarPersonaRequest(
    rut: Option[String],
    telefono: Option[String],
    correo: Option[String],
    direccion: Option[String],
    comuna: Option[String],
    redSocial: Option[String] = None,
    gestorId: Option[Int] = None,
    anotaciones: Option[String] = None,
    sector: Option[String] = None,
    nombres: String,
    apellidos: Option[String],
    genero: Option[String],
    ocupacion: Option[String] = None,
    fechaNacimiento: Option[LocalDate] = None
  )
  implicit val registrarPersonaFormat: OFormat[RegistrarPersonaRequest] = Json.format[RegistrarPersonaRequest]

  // ===== ENDPOINTS =====

  @cask.get("/api/personas/test")
  def test() = {
    cask.Response(data = "Test", statusCode = 200, headers = corsHeaders)
  }

  @cask.options("/api/personas")
  def personasOptions() = corsOptions()

  @cask.options("/api/personas/:id")
  def personaByIdOptions(id: Int) = corsOptions()

  @cask.options("/api/personas/:id/foto")
  def personaFotoOptions(id: Int) = corsOptions()

  @cask.get("/api/personas")
  def listarPersonas() = {
    try {
      val personasDB = entidadRepo.listarTodasLasPersonas()
      val resultado = personasDB.map { case (entidad, persona) =>
        PersonaCompletaResponse(
          id = entidad.id, rut = entidad.rut, tipoEntidad = entidad.tipoEntidad,
          telefono = entidad.telefono, correo = entidad.correo,
          direccion = entidad.direccion, comuna = entidad.comuna,
          redSocial = entidad.redSocial, gestorId = entidad.gestorId,
          anotaciones = entidad.anotaciones, sector = entidad.sector,
          nombres = persona.nombres, apellidos = persona.apellidos,
          genero = persona.genero, ocupacion = persona.ocupacion,
          fechaNacimiento = persona.fechaNacimiento, fotoUrl = persona.fotoUrl,
          etiquetas = etiquetaRepo.etiquetasPorEntidad(entidad.id)
        )
      }
      respond(Json.toJson(resultado))
    } catch {
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  @cask.get("/api/personas/:id")
  def obtenerPersona(id: Int) = {
    try {
      entidadRepo.obtenerPersonaCompleta(id) match {
        case Some((entidad, persona)) =>
          val response = PersonaCompletaResponse(
            id = entidad.id, rut = entidad.rut, tipoEntidad = entidad.tipoEntidad,
            telefono = entidad.telefono, correo = entidad.correo,
            direccion = entidad.direccion, comuna = entidad.comuna,
            redSocial = entidad.redSocial, gestorId = entidad.gestorId,
            anotaciones = entidad.anotaciones, sector = entidad.sector,
            nombres = persona.nombres, apellidos = persona.apellidos,
            genero = persona.genero, ocupacion = persona.ocupacion,
            fechaNacimiento = persona.fechaNacimiento, fotoUrl = persona.fotoUrl,
            etiquetas = etiquetaRepo.etiquetasPorEntidad(entidad.id)
          )
          respond(Json.toJson(response))
        case None =>
          respond(Json.obj("error" -> s"ID $id no encontrado"), 404)
      }
    } catch {
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  @cask.post("/api/personas")
  def registrarPersona(request: cask.Request): cask.Response[String] = {
    val maybeBody: Either[cask.Response[String], (RegistrarPersonaRequest, Option[FormData], Option[FormDataParser])] = try {
      Right(parseRegistrarPersonaRequest(request))
    } catch {
      case e: Exception =>
        Left(respond(Json.obj("error" -> "JSON inválido o campos faltantes"), 400))
    }

    maybeBody match {
      case Left(errorResponse) => errorResponse
      case Right((body, formDataOpt, parserOpt)) =>
        try {
          validarFotoPersona(formDataOpt)
          val nuevaEntidad = Entidad(
            id = 0, rut = body.rut, tipoEntidad = Some("Persona"),
            telefono = body.telefono, correo = body.correo,
            direccion = body.direccion, comuna = body.comuna,
            redSocial = body.redSocial, gestorId = body.gestorId,
            anotaciones = body.anotaciones, sector = body.sector,
            createdAt = Some(java.time.LocalDateTime.now())
          )
          val nuevaPersona = PersonaNatural(
            entidadId = 0, nombres = body.nombres,
            apellidos = body.apellidos, genero = body.genero,
            ocupacion = body.ocupacion, fechaNacimiento = body.fechaNacimiento
          )
          val idGenerado = entidadRepo.registrarPersonaNatural(nuevaPersona, nuevaEntidad)
          guardarFotoPersonaSiExiste(formDataOpt, idGenerado.toInt).foreach { fotoUrl =>
            entidadRepo.actualizarFotoPersona(idGenerado.toInt, Some(fotoUrl))
          }
          respond(Json.obj("mensaje" -> "Persona creada exitosamente", "id" -> idGenerado), 201)
        } catch {
          case e: org.postgresql.util.PSQLException if e.getSQLState == "23505" =>
            respond(Json.obj("error" -> s"Ya existe una entidad registrada con el RUT ${body.rut.getOrElse("")}"), 409)
          case e: IllegalArgumentException =>
            respond(Json.obj("error" -> e.getMessage), 400)
          case e: Exception =>
            e.printStackTrace()
            respond(Json.obj("error" -> e.getMessage), 500)
        } finally {
          parserOpt.foreach(_.close())
        }
    }
  }

  @cask.get("/api/personas/:id/foto")
  def obtenerFotoPersona(id: Int): cask.model.Response.Raw = {
    try {
      if (id <= 0) throw new IllegalArgumentException("id debe ser mayor a 0")
      entidadRepo.obtenerPersonaCompleta(id).getOrElse(
        throw new NoSuchElementException(s"No existe persona con ID $id")
      )

      val path = buscarFotoPersona(id).getOrElse(
        throw new NoSuchElementException(s"No existe foto para la persona con ID $id")
      )
      val fileName = path.getFileName.toString.replace("\"", "")
      val contentType = Option(Files.probeContentType(path)).getOrElse("application/octet-stream")

      cask.model.StaticFile(
        path = path.toString,
        headers = Seq(
          "Content-Type" -> contentType,
          "Content-Disposition" -> s"""inline; filename="$fileName""""
        ) ++ corsHeaders
      )
    } catch {
      case e: NoSuchElementException => respondRawJson(Json.obj("error" -> e.getMessage), 404)
      case e: IllegalArgumentException => respondRawJson(Json.obj("error" -> e.getMessage), 400)
      case e: Exception =>
        e.printStackTrace()
        respondRawJson(Json.obj("error" -> e.getMessage), 500)
    }
  }

  @cask.post("/api/personas/:id/foto")
  def actualizarFotoPersona(id: Int, request: cask.Request): cask.Response[String] = {
    try {
      if (id <= 0) throw new IllegalArgumentException("id debe ser mayor a 0")
      if (!isMultipart(request)) throw new IllegalArgumentException("Debe enviar multipart/form-data en la subida de foto")
      entidadRepo.obtenerPersonaCompleta(id).getOrElse(
        throw new NoSuchElementException(s"No existe persona con ID $id")
      )

      val parser = FormParserFactory.builder().build().createParser(request.exchange)
      if (parser == null) throw new IllegalArgumentException("No se pudo inicializar parser multipart")
      try {
        val formData = parser.parseBlocking()
        validarFotoPersona(Some(formData))
        eliminarFotoPersona(id)
        val fotoUrl = guardarFotoPersonaSiExiste(Some(formData), id).getOrElse(
          throw new IllegalArgumentException("Debe adjuntar un archivo en el campo 'foto'")
        )
        entidadRepo.actualizarFotoPersona(id, Some(fotoUrl))
        respond(Json.obj("mensaje" -> "Foto actualizada correctamente", "fotoUrl" -> fotoUrl))
      } finally {
        parser.close()
      }
    } catch {
      case e: NoSuchElementException => respond(Json.obj("error" -> e.getMessage), 404)
      case e: IllegalArgumentException => respond(Json.obj("error" -> e.getMessage), 400)
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  @cask.put("/api/personas/:id")
  def editarPersona(id: Int, request: cask.Request) = {
    try {
      val body = Json.parse(request.text()).as[EditarPersonaRequest]
      val entidad = Entidad(
        id = id, rut = body.rut, tipoEntidad = Some(body.tipoEntidad),
        telefono = body.telefono, correo = body.correo,
        direccion = body.direccion, comuna = body.comuna,
        redSocial = body.redSocial, gestorId = body.gestorId,
        anotaciones = body.anotaciones, sector = body.sector,
        createdAt = None
      )
      val persona = PersonaNatural(
        entidadId = id, nombres = body.nombres,
        apellidos = body.apellidos, genero = body.genero,
        ocupacion = body.ocupacion, fechaNacimiento = body.fechaNacimiento
      )
      val rowsUpdated = entidadRepo.actualizarPersonaNatural(id, persona, entidad)
      if (rowsUpdated > 0) {
        respond(Json.obj("mensaje" -> "Persona actualizada exitosamente", "filasActualizadas" -> rowsUpdated))
      } else {
        respond(Json.obj("error" -> s"No se encontró la persona con ID $id"), 404)
      }
    } catch {
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  @cask.delete("/api/personas/:id")
  def eliminarPersona(id: Int) = {
    try {
      val eliminado = entidadRepo.eliminarPersona(id)
      if (eliminado) {
        eliminarFotoPersona(id)
        respond(Json.obj("mensaje" -> "Persona eliminada exitosamente"))
      } else {
        respond(Json.obj("error" -> "No se encontró la persona con ese ID"), 404)
      }
    } catch {
      case e: org.postgresql.util.PSQLException if e.getSQLState == "23503" =>
        respond(Json.obj("error" -> "No se puede eliminar la persona porque tiene registros asociados"), 409)
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  initialize()

  private def parseRegistrarPersonaRequest(request: cask.Request): (RegistrarPersonaRequest, Option[FormData], Option[FormDataParser]) = {
    if (isMultipart(request)) {
      val parser = FormParserFactory.builder().build().createParser(request.exchange)
      if (parser == null) throw new IllegalArgumentException("No se pudo inicializar parser multipart")
      val formData = parser.parseBlocking()
      val fechaNacimiento = formValue(formData, "fechaNacimiento").map(LocalDate.parse)
      val body = RegistrarPersonaRequest(
        rut = formValue(formData, "rut"),
        telefono = formValue(formData, "telefono"),
        correo = formValue(formData, "correo"),
        direccion = formValue(formData, "direccion"),
        comuna = formValue(formData, "comuna"),
        redSocial = formValue(formData, "redSocial"),
        gestorId = formValue(formData, "gestorId").map(_.toInt),
        anotaciones = formValue(formData, "anotaciones"),
        sector = formValue(formData, "sector"),
        nombres = formValue(formData, "nombres").getOrElse(throw new IllegalArgumentException("nombres es obligatorio")),
        apellidos = formValue(formData, "apellidos"),
        genero = formValue(formData, "genero"),
        ocupacion = formValue(formData, "ocupacion"),
        fechaNacimiento = fechaNacimiento
      )
      (body, Some(formData), Some(parser))
    } else {
      (Json.parse(request.text()).as[RegistrarPersonaRequest], None, None)
    }
  }

  private def guardarFotoPersonaSiExiste(formDataOpt: Option[FormData], personaId: Int): Option[String] = {
    formDataOpt.flatMap { formData =>
      val fotoForm = Option(formData.getFirst("foto")).filter(_.isFile)
      fotoForm.map { foto =>
        val sourcePath = Option(foto.getPath)
          .orElse(Option(foto.getFile).map(_.toPath))
          .getOrElse(throw new IllegalArgumentException("No se pudo leer la foto adjunta"))
        if (!Files.exists(sourcePath)) throw new IllegalArgumentException("El archivo temporal de la foto no está disponible")
        val size = Files.size(sourcePath)
        if (size > maxFotoBytes) throw new IllegalArgumentException("La foto no puede superar 5 MB")

        val extension = extraerExtensionImagen(Option(foto.getFileName))
        Files.createDirectories(fotosPersonasDir)
        val targetFileName = s"persona_${personaId}$extension"
        val targetPath = fotosPersonasDir.resolve(targetFileName).normalize()
        Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING)
        s"/api/personas/$personaId/foto"
      }
    }
  }

  private def validarFotoPersona(formDataOpt: Option[FormData]): Unit = {
    formDataOpt
      .flatMap(formData => Option(formData.getFirst("foto")).filter(_.isFile))
      .foreach { foto =>
        val sourcePath = Option(foto.getPath)
          .orElse(Option(foto.getFile).map(_.toPath))
          .getOrElse(throw new IllegalArgumentException("No se pudo leer la foto adjunta"))
        if (!Files.exists(sourcePath)) throw new IllegalArgumentException("El archivo temporal de la foto no está disponible")
        if (Files.size(sourcePath) > maxFotoBytes) throw new IllegalArgumentException("La foto no puede superar 5 MB")
        extraerExtensionImagen(Option(foto.getFileName))
      }
  }

  private def formValue(formData: FormData, key: String): Option[String] = {
    Option(formData.getFirst(key)).map(_.getValue).map(_.trim).filter(_.nonEmpty)
  }

  private def isMultipart(request: cask.Request): Boolean = {
    Option(request.exchange.getRequestHeaders.getFirst("Content-Type"))
      .exists(_.toLowerCase.startsWith("multipart/form-data"))
  }

  private def extraerExtensionImagen(fileNameOpt: Option[String]): String = {
    val fileName = fileNameOpt.getOrElse("").trim
    val dot = fileName.lastIndexOf('.')
    val rawExt = if (dot < 0 || dot == fileName.length - 1) "jpg" else fileName.substring(dot + 1).toLowerCase
    rawExt.replaceAll("[^a-z0-9]", "") match {
      case "jpg" | "jpeg" => ".jpg"
      case "png" => ".png"
      case "webp" => ".webp"
      case _ => throw new IllegalArgumentException("Formato de foto no permitido. Use JPG, PNG o WEBP")
    }
  }

  private def buscarFotoPersona(personaId: Int): Option[Path] = {
    if (personaId <= 0 || !Files.isDirectory(fotosPersonasDir)) return None
    val fileNamePrefix = s"persona_$personaId."
    val stream = Files.list(fotosPersonasDir)
    try {
      stream.iterator().asScala
        .filter(path => Files.isRegularFile(path))
        .find(path => path.getFileName.toString.startsWith(fileNamePrefix))
    } finally {
      stream.close()
    }
  }

  private def eliminarFotoPersona(personaId: Int): Unit = {
    try {
      buscarFotoPersona(personaId).foreach(path => Files.deleteIfExists(path))
    } catch {
      case e: Exception => e.printStackTrace()
    }
  }

  private def respondRawJson(data: JsValue, statusCode: Int): cask.model.Response.Raw = {
    cask.Response(
      data = data.toString(),
      statusCode = statusCode,
      headers = Seq("Content-Type" -> "application/json") ++ corsHeaders
    )
  }

  private val fotosPersonasDir: Path = Paths.get(sys.env.getOrElse("PERSONAS_FOTOS_DIR", "personas-fotos"))
  private val maxFotoBytes: Long = 5L * 1024L * 1024L
}
