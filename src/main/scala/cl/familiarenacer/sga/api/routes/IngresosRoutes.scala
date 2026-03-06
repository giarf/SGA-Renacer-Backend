package cl.familiarenacer.sga.api.routes

import cl.familiarenacer.sga.api.ApiSupport
import cl.familiarenacer.sga.modelos._
import cl.familiarenacer.sga.repositorios.{DonacionRepository, InventarioRepository}
import io.undertow.server.handlers.form.{FormData, FormParserFactory}
import play.api.libs.json._

import java.time.LocalDate
import java.nio.file.{Files, Path, Paths, StandardCopyOption}
import scala.jdk.CollectionConverters._

class IngresosRoutes(
  donacionRepo: DonacionRepository,
  inventarioRepo: InventarioRepository
)(implicit cc: castor.Context, log: cask.Logger) extends cask.Routes with ApiSupport {

  // ===== DTOs =====

  case class DonacionRequest(ingreso: IngresoRecurso, donacion: IngresoDonacion, pecuniario: IngresoPecuniario)
  implicit val donacionRequestFormat: OFormat[DonacionRequest] = Json.format[DonacionRequest]

  case class ItemDonacionRequest(
    itemCatalogoId: Option[Int] = None,
    nombre: String,
    categoria: Option[String],
    unidad: Option[String],
    cantidad: BigDecimal,
    precio: BigDecimal
  )
  implicit val itemDonacionFormat: OFormat[ItemDonacionRequest] = Json.format[ItemDonacionRequest]

  case class RegistrarDonacionBienesRequest(
    ingreso: IngresoRecurso,
    donacion: IngresoDonacion,
    items: List[ItemDonacionRequest]
  )
  implicit val registrarDonacionBienesFormat: OFormat[RegistrarDonacionBienesRequest] = Json.format[RegistrarDonacionBienesRequest]

  case class RegistrarCompraRequest(
    ingreso: IngresoRecurso,
    compra: IngresoCompra,
    detalles: List[DetalleIngresoRecurso]
  )
  implicit val registrarCompraFormat: OFormat[RegistrarCompraRequest] = Json.format[RegistrarCompraRequest]

  case class RegistrarPecuniarioRequest(
    ingreso: IngresoRecurso,
    pecuniario: IngresoPecuniario
  )
  implicit val registrarPecuniarioFormat: OFormat[RegistrarPecuniarioRequest] = Json.format[RegistrarPecuniarioRequest]

  case class RegistrarSubvencionRequest(
    ingreso: IngresoRecurso,
    subvencion: IngresoSubvencion,
    pecuniario: Option[IngresoPecuniario]
  )
  implicit val registrarSubvencionFormat: OFormat[RegistrarSubvencionRequest] = Json.format[RegistrarSubvencionRequest]

  // ===== ENDPOINTS =====

  @cask.options("/api/ingresos")
  def ingresosOptions() = corsOptions()

  @cask.get("/api/ingresos")
  def listarIngresos() = {
    try {
      val ingresos = donacionRepo.listarIngresosConTipo()
      respond(Json.toJson(ingresos))
    } catch {
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  @cask.options("/api/ingresos/donacion")
  def donacionOptions() = corsOptions()

  @cask.post("/api/ingresos/donacion")
  def registrarDonacion(request: cask.Request) = {
    try {
      val body = Json.parse(request.text()).as[DonacionRequest]
      val ingresoNormalizado = normalizarIngreso(body.ingreso)
      val id = donacionRepo.registrarDonacionDinero(ingresoNormalizado, body.donacion, body.pecuniario)
      respond(Json.obj("id_ingreso" -> id, "status" -> "registrado"), 201)
    } catch {
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  @cask.options("/api/ingresos/donacion-bienes")
  def donacionBienesOptions() = corsOptions()

  @cask.post("/api/ingresos/donacion-bienes")
  def registrarDonacionBienes(request: cask.Request) = {
    try {
      val body = Json.parse(request.text()).as[RegistrarDonacionBienesRequest]
      val ingresoNormalizado = normalizarIngreso(body.ingreso)
      val detallesInput = body.items.map { item =>
        DetalleDonacionInput(
          detalle = DetalleIngresoRecurso(
            id = 0,
            ingresoId = None,
            itemCatalogoId = item.itemCatalogoId.filter(_ > 0),
            cantidad = Some(item.cantidad),
            precioUnitarioIngreso = Some(item.precio)
          ),
          nombreItem = item.nombre,
          categoria = item.categoria,
          unidadMedida = item.unidad
        )
      }
      val id = inventarioRepo.registrarDonacionCompleta(ingresoNormalizado, body.donacion, detallesInput)
      respond(Json.obj("id_ingreso" -> id, "mensaje" -> "Donación de bienes registrada exitosamente"), 201)
    } catch {
      case e: IllegalArgumentException =>
        respond(Json.obj("error" -> e.getMessage), 400)
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  @cask.options("/api/ingresos/compra")
  def compraOptions() = corsOptions()

  @cask.options("/api/ingresos/compras")
  def comprasOptions() = corsOptions()

  @cask.options("/api/ingresos/compra/boleta")
  def compraBoletaOptions() = corsOptions()

  @cask.options("/api/ingresos/compra/boleta/:ingresoId")
  def compraBoletaByIdOptions(ingresoId: Int) = corsOptions()

  @cask.options("/api/ingresos/compra/boleta/:ingresoId/download")
  def compraBoletaDownloadOptions(ingresoId: Int) = corsOptions()

  @cask.post("/api/ingresos/compra")
  def registrarCompra(request: cask.Request) = {
    try {
      val body = Json.parse(request.text()).as[RegistrarCompraRequest]
      val ingresoNormalizado = normalizarIngreso(body.ingreso)
      val id = donacionRepo.registrarCompra(ingresoNormalizado, body.compra, body.detalles)
      respond(Json.obj("id_ingreso" -> id, "mensaje" -> "Compra registrada exitosamente"), 201)
    } catch {
      case e: IllegalArgumentException =>
        respond(Json.obj("error" -> e.getMessage), 400)
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  @cask.get("/api/ingresos/compras")
  def listarCompras() = {
    try {
      val compras = donacionRepo
        .listarIngresosConTipo()
        .filter(_.tipo == "Compra")
        .map { compra =>
          val boletaOpt = buscarBoletaCompra(compra.id)
          Json.obj(
            "id" -> compra.id,
            "tipo" -> compra.tipo,
            "fecha" -> compra.fecha,
            "montoTotal" -> compra.montoTotal,
            "estado" -> compra.estado,
            "descripcion" -> compra.descripcion,
            "tieneBoleta" -> boletaOpt.isDefined,
            "boletaEndpoint" -> boletaOpt.map(_ => s"/api/ingresos/compra/boleta/${compra.id}"),
            "boletaDownloadEndpoint" -> boletaOpt.map(_ => s"/api/ingresos/compra/boleta/${compra.id}/download")
          )
        }

      respond(Json.toJson(compras))
    } catch {
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  @cask.post("/api/ingresos/compra/boleta")
  def adjuntarBoletaCompra(request: cask.Request) = {
    try {
      val contentType = Option(request.exchange.getRequestHeaders.getFirst("Content-Type"))
        .getOrElse("")
        .toLowerCase
      if (!contentType.startsWith("multipart/form-data")) {
        throw new IllegalArgumentException("Debe enviar multipart/form-data en la subida de boleta")
      }

      val parser = FormParserFactory.builder().build().createParser(request.exchange)
      if (parser == null) throw new IllegalArgumentException("No se pudo inicializar parser multipart")
      try {
        val formData = parser.parseBlocking()

        val boletaCompraId = Option(formData.getFirst("boletaCompraId"))
          .orElse(Option(formData.getFirst("boleta_compra_id")))
          .map(_.getValue)
          .getOrElse(throw new IllegalArgumentException("boletaCompraId es obligatorio"))
          .trim
          .toInt

        if (boletaCompraId <= 0) {
          throw new IllegalArgumentException("boletaCompraId debe ser mayor a 0")
        }

        // Validamos que el ingreso exista antes de guardar la boleta.
        donacionRepo.obtenerIngreso(boletaCompraId).getOrElse(
          throw new NoSuchElementException(s"No existe compra/ingreso con ID $boletaCompraId")
        )

        val boletaForm = Option(formData.getFirst("boleta"))
          .filter(_.isFile)
          .getOrElse(throw new IllegalArgumentException("Debe adjuntar un archivo en el campo 'boleta'"))

        val sourcePath = Option(boletaForm.getPath)
          .orElse(Option(boletaForm.getFile).map(_.toPath))
          .getOrElse(throw new IllegalArgumentException("No se pudo leer el archivo de boleta adjunto"))

        if (!Files.exists(sourcePath)) {
          throw new IllegalArgumentException("El archivo temporal de la boleta no está disponible")
        }

        val extension = extraerExtensionSegura(Option(boletaForm.getFileName))
        Files.createDirectories(boletasDir)

        val targetFileName = s"boleta_compra_${boletaCompraId}$extension"
        val targetPath = boletasDir.resolve(targetFileName).normalize()
        Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING)

        respond(
          Json.obj(
            "mensaje" -> "Boleta guardada correctamente en disco local",
            "boleta_compra_id" -> boletaCompraId,
            "archivo" -> targetPath.toString
          ),
          201
        )
      } finally {
        parser.close()
      }
    } catch {
      case e: NumberFormatException =>
        respond(Json.obj("error" -> "boletaCompraId debe ser numérico"), 400)
      case e: NoSuchElementException =>
        respond(Json.obj("error" -> e.getMessage), 404)
      case e: IllegalArgumentException =>
        respond(Json.obj("error" -> e.getMessage), 400)
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  @cask.get("/api/ingresos/compra/boleta/:ingresoId")
  def obtenerBoletaCompra(ingresoId: Int) = {
    try {
      if (ingresoId <= 0) throw new IllegalArgumentException("ingresoId debe ser mayor a 0")

      donacionRepo.obtenerIngreso(ingresoId).getOrElse(
        throw new NoSuchElementException(s"No existe compra/ingreso con ID $ingresoId")
      )

      buscarBoletaCompra(ingresoId) match {
        case Some(path) =>
          respond(
            Json.obj(
              "boleta_compra_id" -> ingresoId,
              "tieneBoleta" -> true,
              "archivo" -> path.toString,
              "nombreArchivo" -> path.getFileName.toString,
              "downloadEndpoint" -> s"/api/ingresos/compra/boleta/$ingresoId/download"
            )
          )
        case None =>
          respond(
            Json.obj(
              "boleta_compra_id" -> ingresoId,
              "tieneBoleta" -> false
            ),
            404
          )
      }
    } catch {
      case e: NoSuchElementException =>
        respond(Json.obj("error" -> e.getMessage), 404)
      case e: IllegalArgumentException =>
        respond(Json.obj("error" -> e.getMessage), 400)
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  @cask.get("/api/ingresos/compra/boleta/:ingresoId/download")
  def descargarBoletaCompra(ingresoId: Int): cask.model.Response.Raw = {
    try {
      if (ingresoId <= 0) throw new IllegalArgumentException("ingresoId debe ser mayor a 0")

      donacionRepo.obtenerIngreso(ingresoId).getOrElse(
        throw new NoSuchElementException(s"No existe compra/ingreso con ID $ingresoId")
      )

      val path = buscarBoletaCompra(ingresoId).getOrElse(
        throw new NoSuchElementException(s"No existe boleta para la compra/ingreso con ID $ingresoId")
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
      case e: NoSuchElementException =>
        respondRawJson(Json.obj("error" -> e.getMessage), 404)
      case e: IllegalArgumentException =>
        respondRawJson(Json.obj("error" -> e.getMessage), 400)
      case e: Exception =>
        e.printStackTrace()
        respondRawJson(Json.obj("error" -> e.getMessage), 500)
    }
  }

  @cask.options("/api/ingresos/subvencion")
  def subvencionOptions() = corsOptions()

  @cask.post("/api/ingresos/subvencion")
  def registrarSubvencion(request: cask.Request) = {
    try {
      val body = Json.parse(request.text()).as[RegistrarSubvencionRequest]
      val ingresoNormalizado = normalizarIngreso(body.ingreso)
      val id = donacionRepo.registrarSubvencion(ingresoNormalizado, body.subvencion, body.pecuniario)
      respond(Json.obj("id_ingreso" -> id, "mensaje" -> "Subvención registrada exitosamente"), 201)
    } catch {
      case e: IllegalArgumentException =>
        respond(Json.obj("error" -> e.getMessage), 400)
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  initialize()

  private def normalizarIngreso(ingreso: IngresoRecurso): IngresoRecurso = {
    if (ingreso.fecha.isDefined) ingreso else ingreso.copy(fecha = Some(LocalDate.now()))
  }

  private def extraerExtensionSegura(fileNameOpt: Option[String]): String = {
    val fileName = fileNameOpt.getOrElse("").trim
    val dot = fileName.lastIndexOf('.')
    if (dot < 0 || dot == fileName.length - 1) ""
    else {
      val rawExt = fileName.substring(dot + 1).toLowerCase
      val sanitized = rawExt.replaceAll("[^a-z0-9]", "")
      if (sanitized.isEmpty) "" else s".$sanitized"
    }
  }

  private val boletasDir: Path = Paths.get("boletas")

  private def respondRawJson(data: JsValue, statusCode: Int): cask.model.Response.Raw = {
    cask.Response(
      data = data.toString(),
      statusCode = statusCode,
      headers = Seq("Content-Type" -> "application/json") ++ corsHeaders
    )
  }

  private def buscarBoletaCompra(ingresoId: Int): Option[Path] = {
    if (ingresoId <= 0 || !Files.isDirectory(boletasDir)) return None

    val fileNameExact = s"boleta_compra_$ingresoId"
    val fileNamePrefix = s"boleta_compra_$ingresoId."
    val stream = Files.list(boletasDir)
    try {
      stream.iterator().asScala
        .filter(path => Files.isRegularFile(path))
        .find { path =>
          val fileName = path.getFileName.toString
          fileName == fileNameExact || fileName.startsWith(fileNamePrefix)
        }
    } finally {
      stream.close()
    }
  }
}
