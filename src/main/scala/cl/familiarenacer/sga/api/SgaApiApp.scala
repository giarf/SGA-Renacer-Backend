package cl.familiarenacer.sga.api

import cask.model.Response
import cl.familiarenacer.sga.modelos._
import cl.familiarenacer.sga.repositorios.{DB, DonacionRepository, EntidadRepository, InventarioRepository, InstitucionRepository, RolesRepository, FamiliaRepository, EgresoRepository, SolicitudRepository}
import play.api.libs.json._
import java.time.LocalDate

/**
 * Servidor Web API REST para SGA Renacer.
 * Utiliza Cask para el enrutamiento y Play JSON para la serialización.
 */
object SgaApiApp extends cask.MainRoutes {

  override def host = "0.0.0.0"
  override def port = 8080

  // Inicialización de Repositorios
  val entidadRepo = new EntidadRepository(DB.ctx)
  val donacionRepo = new DonacionRepository(DB.ctx)
  val inventarioRepo = new InventarioRepository(DB.ctx)
  val institucionRepo = new InstitucionRepository(DB.ctx)
  val rolesRepo = new RolesRepository(DB.ctx)
  val familiaRepo = new FamiliaRepository(DB.ctx)
  val egresoRepo = new EgresoRepository(DB.ctx)
  val solicitudRepo = new SolicitudRepository(DB.ctx)

  // Formatos JSON Implicítos para los modelos
  implicit val entidadFormat: OFormat[Entidad] = Json.format[Entidad]
  implicit val personaFormat: OFormat[PersonaNatural] = Json.format[PersonaNatural]
  implicit val institucionFormat: OFormat[Institucion] = Json.format[Institucion]
  implicit val beneficiarioFormat: OFormat[Beneficiario] = Json.format[Beneficiario]
  implicit val colaboradorFormat: OFormat[Colaborador] = Json.format[Colaborador]
  implicit val trabajadorFormat: OFormat[Trabajador] = Json.format[Trabajador]
  implicit val directivoFormat: OFormat[Directivo] = Json.format[Directivo]
  implicit val familiaFormat: OFormat[Familia] = Json.format[Familia]
  implicit val ingresoFormat: OFormat[IngresoRecurso] = Json.format[IngresoRecurso]
  implicit val donacionFormat: OFormat[IngresoDonacion] = Json.format[IngresoDonacion]
  implicit val pecuniarioFormat: OFormat[IngresoPecuniario] = Json.format[IngresoPecuniario]
  implicit val compraFormat: OFormat[IngresoCompra] = Json.format[IngresoCompra]
  implicit val subvencionFormat: OFormat[IngresoSubvencion] = Json.format[IngresoSubvencion]
  implicit val detalleIngresoFormat: OFormat[DetalleIngresoRecurso] = Json.format[DetalleIngresoRecurso]
  implicit val egresoFormat: OFormat[EgresoRecurso] = Json.format[EgresoRecurso]
  implicit val ayudaSocialFormat: OFormat[EgresoAyudaSocial] = Json.format[EgresoAyudaSocial]
  implicit val consumoInternoFormat: OFormat[EgresoConsumoInterno] = Json.format[EgresoConsumoInterno]
  implicit val detalleEgresoFormat: OFormat[DetalleEgresoRecurso] = Json.format[DetalleEgresoRecurso]
  implicit val solicitudFormat: OFormat[SolicitudMaterial] = Json.format[SolicitudMaterial]
  implicit val itemSolicitudFormat: OFormat[ItemSolicitud] = Json.format[ItemSolicitud]

  // Nuevo DTO
  implicit val resumenFormat: OFormat[EntidadResumen] = Json.format[EntidadResumen]

  // Estructura auxiliar para recibir donación completa
  case class DonacionRequest(ingreso: IngresoRecurso, donacion: IngresoDonacion, pecuniario: IngresoPecuniario)
  implicit val donacionRequestFormat: OFormat[DonacionRequest] = Json.format[DonacionRequest]

  // Estructura auxiliar para request de edición de entidad
  case class EditarEntidadRequest(id: Int, rut: String, correo: Option[String], telefono: Option[String])
  implicit val editarEntidadFormat: OFormat[EditarEntidadRequest] = Json.format[EditarEntidadRequest]

  // Estructura auxiliar para editar persona natural
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
    fechaNacimiento: Option[LocalDate] = None
  )
  implicit val editarPersonaFormat: OFormat[EditarPersonaRequest] = Json.format[EditarPersonaRequest]

  // DTO de respuesta para persona completa
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
    fechaNacimiento: Option[LocalDate] = None
  )
  implicit val personaCompletaFormat: OFormat[PersonaCompletaResponse] = Json.format[PersonaCompletaResponse]

  // Estructura auxiliar para crear solicitud
  case class SolicitudRequest(solicitud: SolicitudMaterial, items: List[ItemSolicitud])
  implicit val solicitudRequestFormat: OFormat[SolicitudRequest] = Json.format[SolicitudRequest]

  // DTOs para Donación de Bienes
  case class ItemDonacionRequest(
    id: Int,                           // Siempre 0 para nuevos items
    itemCatalogoId: Int,               // 0 si es nuevo, >0 si ya existe
    nombre: String,                    // Nombre del ítem
    categoria: Option[String],         // Categoría (para ítems nuevos)
    unidad: Option[String],            // Unidad de medida (para ítems nuevos)
    cantidad: BigDecimal,              // Cantidad donada
    precio: BigDecimal                 // Precio unitario estimado
  )
  implicit val itemDonacionFormat: OFormat[ItemDonacionRequest] = Json.format[ItemDonacionRequest]

  case class RegistrarDonacionBienesRequest(
    ingreso: IngresoRecurso,
    donacion: IngresoDonacion,
    items: List[ItemDonacionRequest]
  )
  implicit val registrarDonacionBienesFormat: OFormat[RegistrarDonacionBienesRequest] = Json.format[RegistrarDonacionBienesRequest]

  // DTO para respuesta de utilitarios (Categorias y Unidades)
  case class UtilitariosResponse(categorias: List[String], unidades: List[String])
  implicit val utilitariosResponseFormat: OFormat[UtilitariosResponse] = Json.format[UtilitariosResponse]


  // Decorador para CORS eliminado en favor del global

  /**
  * Test
  */
  @cask.get("/api/personas/test")
  def test() = {
    cask.Response(
      data = "Test",
      statusCode = 200,
      headers = corsHeaders
    )
  }
  /**
   * Endpoint: Listar Entidades
   * GET /api/entidades?tipo=Persona&q=nombre
   * Retorna una lista unificada de EntidadResumen.
   */
  @cask.get("/api/entidades")
  def listarEntidades(tipo: Option[String] = None, q: Option[String] = None) = {
    try {
      val resultado = entidadRepo.listarEntidadesUnificadas(tipo, q)
      cask.Response(Json.toJson(resultado).toString(), 200, headers = Seq("Content-Type" -> "application/json"))
    } catch {
      case e: Exception =>
        cask.Response(Json.obj("error" -> e.getMessage).toString(), 500)
    }
  }


  /**
   * Endpoint: OPTIONS para CORS Preflight - Operaciones sobre Persona (GET, PUT, DELETE)
   * OPTIONS /api/personas/:id
   */
  @cask.options("/api/personas/:id")
  def personaOptions(id: Int) = {
    cask.Response(
      data = "",
      statusCode = 204,
      headers = corsHeaders
    )
  }

  /**
   * Endpoint: Editar Persona Natural
   * PUT /api/personas/3
   * Actualiza datos de una persona y su entidad base.
   */
  @cask.put("/api/personas/:id")
  def editarPersona(id: Int, request: cask.Request) = {
    try {
      val body = Json.parse(request.text()).as[EditarPersonaRequest]
      
      // Override ID from path ensures consistency
      val targetId = id

      // Crear objetos para actualizar
      val entidad = Entidad(
        id = targetId,
        rut = body.rut,
        tipoEntidad = Some(body.tipoEntidad),
        telefono = body.telefono,
        correo = body.correo,
        direccion = body.direccion,
        comuna = body.comuna,
        redSocial = body.redSocial,
        gestorId = body.gestorId,
        anotaciones = body.anotaciones,
        sector = body.sector,
        createdAt = None  // No actualizamos createdAt
      )
      
      val persona = PersonaNatural(
        entidadId = targetId,
        nombres = body.nombres,
        apellidos = body.apellidos,
        genero = body.genero,
        ocupacion = body.ocupacion,
        fechaNacimiento = body.fechaNacimiento
      )
      
      val rowsUpdated = entidadRepo.actualizarPersonaNatural(targetId, persona, entidad)
      
      if (rowsUpdated > 0) {
        respond(Json.obj("mensaje" -> "Persona actualizada exitosamente", "filasActualizadas" -> rowsUpdated))
      } else {
        respond(Json.obj("error" -> s"No se encontró la persona con ID $targetId"), 404)
      }
    } catch {
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  /**
   * Endpoint: Eliminar Persona
   * DELETE /api/personas/3
   * Elimina una persona y su entidad asociada.
   */
  @cask.delete("/api/personas/:id")
  def eliminarPersona(id: Int) = {
    try {
      val eliminado = entidadRepo.eliminarPersona(id)
      if (eliminado) {
        respond(Json.obj("mensaje" -> "Persona eliminada exitosamente"))
      } else {
        respond(Json.obj("error" -> "No se encontró la persona con ese ID"), 404)
      }
    } catch {
      case e: org.postgresql.util.PSQLException if e.getSQLState == "23503" =>
        respond(Json.obj("error" -> "No se puede eliminar la persona porque tiene registros asociados (Donaciones, Ingresos, etc.)"), 409)
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }
  /**
   * Endpoint: Listar Todas las Personas
   * GET /api/personas
   * Retorna un arreglo [] con la ficha básica de cada persona.
   */
  @cask.get("/api/personas")
  def listarPersonas() = {
    try {
      val personasDB = entidadRepo.listarTodasLasPersonas()
      
      val resultado = personasDB.map { case (entidad, persona) =>
        PersonaCompletaResponse(
          id = entidad.id,
          rut = entidad.rut,
          tipoEntidad = entidad.tipoEntidad,
          telefono = entidad.telefono,
          correo = entidad.correo,
          direccion = entidad.direccion,
          comuna = entidad.comuna,
          redSocial = entidad.redSocial,
          gestorId = entidad.gestorId,
          anotaciones = entidad.anotaciones,
          sector = entidad.sector,
          nombres = persona.nombres,
          apellidos = persona.apellidos,
          genero = persona.genero,
          ocupacion = persona.ocupacion,
          fechaNacimiento = persona.fechaNacimiento
        )
      }
      
      respond(Json.toJson(resultado))
      
    } catch {
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }

  /**
   * Endpoint: Obtener Persona por ID
   * GET /api/personas/3
   * Retorna los datos completos de una persona (Entidad + PersonaNatural).
   */
  @cask.get("/api/personas/:id")
  def obtenerPersona(id: Int) = {
    println(s"[DEBUG] Request Recibido - Endpoint: /api/personas/$id")
    try {
      entidadRepo.obtenerPersonaCompleta(id) match {
        case Some((entidad, persona)) =>
          val response = PersonaCompletaResponse(
            id = entidad.id,
            rut = entidad.rut,
            tipoEntidad = entidad.tipoEntidad,
            telefono = entidad.telefono,
            correo = entidad.correo,
            direccion = entidad.direccion,
            comuna = entidad.comuna,
            redSocial = entidad.redSocial,
            gestorId = entidad.gestorId,
            anotaciones = entidad.anotaciones,
            sector = entidad.sector,
            nombres = persona.nombres,
            apellidos = persona.apellidos,
            genero = persona.genero,
            ocupacion = persona.ocupacion,
            fechaNacimiento = persona.fechaNacimiento
          )
          respond(Json.toJson(response))
        case None =>
          println(s"[DEBUG] Persona con ID $id no encontrada")
          respond(Json.obj("error" -> s"ID $id no encontrado"), 404)
      }
    } catch {
      case e: Exception =>
        e.printStackTrace()
        respond(Json.obj("error" -> e.getMessage), 500)
    }
  }
    

  /**
   * Endpoint: Registrar Donación de Dinero
   * POST /api/ingresos/donacion
   * Cuerpo JSON: { "ingreso": {...}, "donacion": {...}, "pecuniario": {...} }
   */
  @cask.post("/api/ingresos/donacion")
  def registrarDonacion(request: cask.Request) = {
    try {
      val body = Json.parse(request.text()).as[DonacionRequest]
      val id = donacionRepo.registrarDonacionDinero(body.ingreso, body.donacion, body.pecuniario)
      cask.Response(Json.obj("id_ingreso" -> id, "status" -> "registrado").toString(), 201, headers = Seq("Content-Type" -> "application/json"))
    } catch {
      case e: Exception =>
        cask.Response(Json.obj("error" -> e.getMessage).toString(), 500, headers = Seq("Content-Type" -> "application/json"))
    }
  }

  /**
   * Endpoint: OPTIONS para CORS Preflight - Donación de Bienes
   * OPTIONS /api/ingresos/donacion-bienes
   */
  @cask.options("/api/ingresos/donacion-bienes")
  def donacionBienesOptions() = {
    cask.Response(
      data = "",
      statusCode = 204,
      headers = corsHeaders
    )
  }

  /**
   * Endpoint: Registrar Donación de Bienes (No Pecuniaria)
   * POST /api/ingresos/donacion-bienes
   * Cuerpo JSON: RegistrarDonacionBienesRequest
   */
  @cask.post("/api/ingresos/donacion-bienes")
  def registrarDonacionBienesEndpoint(request: cask.Request) = {
    try {
      val body = Json.parse(request.text()).as[RegistrarDonacionBienesRequest]
      
      // Mapeo del DTO de entrada al modelo interno del repositorio
      val detallesInput = body.items.map { item =>
        DetalleDonacionInput(
          detalle = DetalleIngresoRecurso(
            id = 0,                                           // Siempre 0, la DB auto-genera
            ingresoId = None,                                 // Se llena al insertar
            itemCatalogoId = if (item.itemCatalogoId > 0) Some(item.itemCatalogoId) else None, // Si viene >0, usar ese ID
            cantidad = Some(item.cantidad),
            precioUnitarioIngreso = Some(item.precio)
          ),
          nombreItem = item.nombre,
          categoria = item.categoria,
          unidadMedida = item.unidad
        )
      }

      val id = inventarioRepo.registrarDonacionCompleta(body.ingreso, body.donacion, detallesInput)
      
      cask.Response(Json.obj("id_ingreso" -> id, "mensaje" -> "Donación de bienes registrada exitosamente").toString(), 201, headers = Seq("Content-Type" -> "application/json"))
    } catch {
      case e: IllegalArgumentException =>
        cask.Response(Json.obj("error" -> e.getMessage).toString(), 400, headers = Seq("Content-Type" -> "application/json"))
      case e: Exception =>
        e.printStackTrace()
        cask.Response(Json.obj("error" -> e.getMessage).toString(), 500, headers = Seq("Content-Type" -> "application/json"))
    }
  }

  /**
   * Endpoint: Buscar Ítems
   * GET /api/catalogo/buscar?q=...
   */
  @cask.get("/api/catalogo/buscar")
  def buscarItems(q: String) = {
    try {
      val resultados = inventarioRepo.buscarItems(q)
      // Retornamos JSON con estructura simple
      implicit val itemFormat: OFormat[ItemCatalogo] = Json.format[ItemCatalogo] // Local formatter si no está global
      cask.Response(Json.toJson(resultados).toString(), 200, headers = Seq("Content-Type" -> "application/json"))
    } catch {
      case e: Exception =>
        cask.Response(Json.obj("error" -> e.getMessage).toString(), 500)
    }
  }

  /**
   * Endpoint: Utilitarios de Catálogo (Categorías y Unidades)
   * GET /api/catalogo/utilitarios
   */
  @cask.get("/api/catalogo/utilitarios")
  def catalogoUtilitarios() = {
    try {
      val (cats, units) = inventarioRepo.listarCategoriasUnidades()
      val response = UtilitariosResponse(cats, units)
      cask.Response(Json.toJson(response).toString(), 200, headers = Seq("Content-Type" -> "application/json"))
    } catch {
      case e: Exception =>
        cask.Response(Json.obj("error" -> e.getMessage).toString(), 500)
    }
  }

  /**
   * Endpoint: Listar Todos los Ítems del Catálogo
   * GET /api/catalogo
   */
  @cask.get("/api/catalogo")
  def listarCatalogo() = {
    try {
      val items = inventarioRepo.listarTodosItems()
      implicit val itemFormat: OFormat[ItemCatalogo] = Json.format[ItemCatalogo]
      cask.Response(Json.toJson(items).toString(), 200, headers = Seq("Content-Type" -> "application/json"))
    } catch {
      case e: Exception =>
        e.printStackTrace()
        cask.Response(Json.obj("error" -> e.getMessage).toString(), 500, headers = Seq("Content-Type" -> "application/json"))
    }
  }

  /**
   * Endpoint: OPTIONS para CORS Preflight - Registrar Ítem
   * OPTIONS /api/catalogo/registrar
   */
  @cask.options("/api/catalogo/registrar")
  def registrarItemOptions() = {
    cask.Response(
      data = "",
      statusCode = 204,
      headers = corsHeaders
    )
  }

  /**
   * Endpoint: Registrar Nuevo Ítem en el Catálogo
   * POST /api/catalogo/registrar
   */
  @cask.post("/api/catalogo/registrar")
  def registrarItemCatalogo(request: cask.Request) = {
    try {
      implicit val itemFormat: OFormat[ItemCatalogo] = Json.format[ItemCatalogo]
      val item = Json.parse(request.text()).as[ItemCatalogo]
      
      // Validaciones
      if (item.nombre.isEmpty || item.nombre.exists(_.trim.isEmpty)) {
        cask.Response(Json.obj("error" -> "El nombre del ítem es obligatorio").toString(), 400, headers = Seq("Content-Type" -> "application/json"))
      } else {
        val idGenerado = inventarioRepo.registrarItem(item)
        cask.Response(
          Json.obj("id" -> idGenerado, "mensaje" -> "Ítem registrado exitosamente en el catálogo").toString(),
          201,
          headers = Seq("Content-Type" -> "application/json")
        )
      }
    } catch {
      case e: Exception =>
        e.printStackTrace()
        cask.Response(Json.obj("error" -> e.getMessage).toString(), 500, headers = Seq("Content-Type" -> "application/json"))
    }
  }

  /**
   * DTO para actualizar ítem del catálogo.
   * Solo permite actualizar campos básicos, protege PPP y stock.
   */
  case class ActualizarItemRequest(
    id: Int,
    nombre: Option[String],
    categoria: Option[String],
    unidadMedidaEstandar: Option[String],
    precioReferencia: Option[BigDecimal]
  )
  implicit val actualizarItemFormat: OFormat[ActualizarItemRequest] = Json.format[ActualizarItemRequest]

  /**
   * Endpoint: OPTIONS para CORS Preflight - Actualizar Ítem
   * OPTIONS /api/catalogo/actualizar
   */
  @cask.options("/api/catalogo/actualizar")
  def actualizarItemOptions() = {
    cask.Response(
      data = "",
      statusCode = 204,
      headers = corsHeaders
    )
  }

  /**
   * Endpoint: Actualizar Ítem del Catálogo
   * POST /api/catalogo/actualizar
   * PROTEGE: No permite modificar stockActual, precioPromedioPonderado, valorTotalStock
   */
  @cask.post("/api/catalogo/actualizar")
  def actualizarItemCatalogo(request: cask.Request) = {
    try {
      val body = Json.parse(request.text()).as[ActualizarItemRequest]
      
      val filasActualizadas = inventarioRepo.actualizarItemBasico(
        body.id,
        body.nombre,
        body.categoria,
        body.unidadMedidaEstandar,
        body.precioReferencia
      )
      
      if (filasActualizadas > 0) {
        cask.Response(
          Json.obj("mensaje" -> "Ítem actualizado exitosamente", "filasActualizadas" -> filasActualizadas).toString(),
          200,
          headers = Seq("Content-Type" -> "application/json")
        )
      } else {
        cask.Response(
          Json.obj("error" -> s"No se encontró el ítem con ID ${body.id}").toString(),
          404,
          headers = Seq("Content-Type" -> "application/json")
        )
      }
    } catch {
      case e: Exception =>
        e.printStackTrace()
        cask.Response(Json.obj("error" -> e.getMessage).toString(), 500, headers = Seq("Content-Type" -> "application/json"))
    }
  }

  /**
   * Endpoint: Obtener Categorías Únicas
   * GET /api/catalogo/utilitarios/categorias
   */
  @cask.get("/api/catalogo/utilitarios/categorias")
  def obtenerCategorias() = {
    try {
      val categorias = inventarioRepo.obtenerCategoriasUnicas()
      cask.Response(Json.toJson(categorias).toString(), 200, headers = Seq("Content-Type" -> "application/json"))
    } catch {
      case e: Exception =>
        e.printStackTrace()
        cask.Response(Json.obj("error" -> e.getMessage).toString(), 500, headers = Seq("Content-Type" -> "application/json"))
    }
  }

  /**
   * Endpoint: Crear Solicitud de Material
   * POST /api/solicitudes
   * Cuerpo JSON: { "solicitud": {...}, "items": [...] }
   */
  @cask.post("/api/solicitudes")
  def crearSolicitud(request: cask.Request) = {
    try {
      val body = Json.parse(request.text()).as[SolicitudRequest]
      // Lógica para guardar solicitud e items...
      cask.Response(Json.obj("mensaje" -> "Solicitud recibida", "items_count" -> body.items.size).toString(), 201, headers = Seq("Content-Type" -> "application/json"))
    } catch {
       case e: Exception =>
        cask.Response(Json.obj("error" -> e.getMessage).toString(), 500, headers = Seq("Content-Type"   -> "application/json"))
    }
  }

  // ===== INSTITUCIONES DTOs =====
  case class InstitucionCompletaResponse(
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
    razonSocial: String,
    nombreFantasia: Option[String],
    subtipoInstitucion: Option[String],
    rubro: Option[String]
  )
  implicit val institucionCompletaFormat: OFormat[InstitucionCompletaResponse] = Json.format[InstitucionCompletaResponse]

  case class RegistrarInstitucionRequest(
    rut: Option[String],
    telefono: Option[String],
    correo: Option[String],
    direccion: Option[String],
    comuna: Option[String],
    redSocial: Option[String] = None,
    gestorId: Option[Int] = None,
    anotaciones: Option[String] = None,
    sector: Option[String] = None,
    razonSocial: String,
    nombreFantasia: Option[String],
    subtipoInstitucion: Option[String],
    rubro: Option[String]
  )
  implicit val registrarInstitucionFormat: OFormat[RegistrarInstitucionRequest] = Json.format[RegistrarInstitucionRequest]

  case class EditarInstitucionRequest(
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
    razonSocial: String,
    nombreFantasia: Option[String],
    subtipoInstitucion: Option[String],
    rubro: Option[String]
  )
  implicit val editarInstitucionFormat: OFormat[EditarInstitucionRequest] = Json.format[EditarInstitucionRequest]

  // ===== INGRESOS/EGRESOS DTOs =====
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

  case class RegistrarAyudaSocialRequest(
    egreso: EgresoRecurso,
    ayuda: EgresoAyudaSocial,
    detalles: List[DetalleEgresoRecurso]
  )
  implicit val registrarAyudaSocialFormat: OFormat[RegistrarAyudaSocialRequest] = Json.format[RegistrarAyudaSocialRequest]

  case class RegistrarConsumoInternoRequest(
    egreso: EgresoRecurso,
    consumo: EgresoConsumoInterno,
    detalles: List[DetalleEgresoRecurso]
  )
  implicit val registrarConsumoInternoFormat: OFormat[RegistrarConsumoInternoRequest] = Json.format[RegistrarConsumoInternoRequest]

  // ===== SOLICITUDES DTOs =====
  case class ActualizarSolicitudRequest(
    estado: String,
    autorizadorId: Option[Int]
  )
  implicit val actualizarSolicitudFormat: OFormat[ActualizarSolicitudRequest] = Json.format[ActualizarSolicitudRequest]

  // Estructura auxiliar para request de actualización completa de Persona
  case class ActualizarPersonaRequest(
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
    fechaNacimiento: Option[LocalDate] = None
  )
  implicit val actualizarPersonaFormat: OFormat[ActualizarPersonaRequest] = Json.format[ActualizarPersonaRequest]

  // Estructura para capturar los datos de una nueva persona
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

  /**
   * Endpoint: OPTIONS para CORS Preflight - Registrar Persona
   * OPTIONS /api/personas
   */
  @cask.options("/api/personas")
  def registrarPersonaOptions() = {
    cask.Response(
      data = "",
      statusCode = 204,
      headers = corsHeaders
    )
  }

  /**
   * Endpoint: Registrar Nuevo Persona
   * POST /api/personas
   */
  @cask.post("/api/personas")
  def registrarPersona(request: cask.Request): cask.Response[String] = {
    val maybeBody: Either[cask.Response[String], RegistrarPersonaRequest] = try {
      Right(Json.parse(request.text()).as[RegistrarPersonaRequest])
    } catch {
      case e: Exception =>
        Left(respond(Json.obj("error" -> "JSON inválido o campos faltantes"), 400))
    }

    maybeBody match {
      case Left(errorResponse) => errorResponse
      case Right(body) =>
        try {
          // 1. Construimos el objeto Entidad (sin ID, porque lo genera la DB)
          val nuevaEntidad = Entidad(
            id = 0, // Quill/Postgres ignorará esto al ser auto-incremental
            rut = body.rut,
            tipoEntidad = Some("Persona"),
            telefono = body.telefono,
            correo = body.correo,
            direccion = body.direccion,
            comuna = body.comuna,
            redSocial = body.redSocial,
            gestorId = body.gestorId,
            anotaciones = body.anotaciones,
            sector = body.sector,
            createdAt = Some(java.time.LocalDateTime.now())
          )

          // 2. Construimos el objeto Persona
          val nuevaPersona = PersonaNatural(
            entidadId = 0, // Se llenará con el ID generado de la entidad
            nombres = body.nombres,
            apellidos = body.apellidos,
            genero = body.genero,
            ocupacion = body.ocupacion,
            fechaNacimiento = body.fechaNacimiento
          )

          // 3. Llamada al repositorio (debe ser transaccional)
          val idGenerado = entidadRepo.registrarPersonaNatural(nuevaPersona, nuevaEntidad)

          respond(
            Json.obj("mensaje" -> "Persona creada exitosamente", "id" -> idGenerado),
            201
          )
        } catch {
          case e: org.postgresql.util.PSQLException if e.getSQLState == "23505" =>
            respond(Json.obj("error" -> s"Ya existe una entidad registrada con el RUT ${body.rut.getOrElse("")}"), 409)
          case e: Exception =>
            e.printStackTrace()
            respond(Json.obj("error" -> e.getMessage), 500)
        }
    }
  }

  /**
   * Endpoint: Actualizar Persona Completa
   * POST /api/entidades/actualizar
   * Recibe un objeto JSON con todos los campos de la entidad y persona para actualizar.
   * Realiza una transacción que actualiza ambas tablas.
   */
  @cask.post("/api/entidades/actualizar")
  def actualizarPersonaEndpoint(request: cask.Request) = {
    try {
      val body = Json.parse(request.text()).as[ActualizarPersonaRequest]

      val entidad = Entidad(
        id = body.id,
        rut = body.rut,
        tipoEntidad = body.tipoEntidad,
        telefono = body.telefono,
        correo = body.correo,
        direccion = body.direccion,
        comuna = body.comuna,
        redSocial = body.redSocial,
        gestorId = body.gestorId,
        anotaciones = body.anotaciones,
        sector = body.sector,
        createdAt = None
      )

      val persona = PersonaNatural(
        entidadId = body.id,
        nombres = body.nombres,
        apellidos = body.apellidos,
        genero = body.genero,
        ocupacion = body.ocupacion,
        fechaNacimiento = body.fechaNacimiento
      )

      entidadRepo.actualizarPersona(entidad, persona)

      cask.Response(Json.obj("mensaje" -> s"Persona ${body.id} actualizada correctamente").toString(), 200, headers = Seq("Content-Type" -> "application/json"))
    } catch {
      case e: Exception =>
        cask.Response(Json.obj("error" -> e.getMessage).toString(), 500, headers = Seq("Content-Type" -> "application/json"))
    }
  }

  /**
   * Endpoint: Verificar existencia de RUT
   * GET /api/entidades/existe-rut
   */
  @cask.get("/api/entidades/existe-rut")
  def existeRut(rut: String) = {
    try {
      val existe = entidadRepo.existeRut(rut)
      cask.Response(Json.obj("rut" -> rut, "existe" -> existe).toString(), 200, headers = Seq("Content-Type" -> "application/json"))
    } catch {
      case e: Exception =>
        cask.Response(Json.obj("error" -> e.getMessage).toString(), 500)
    }
  }


  // CORS Headers
  val corsHeaders = Seq(
    "Access-Control-Allow-Origin" -> "http://localhost:5173",
    "Access-Control-Allow-Methods" -> "GET, POST, PUT, DELETE, OPTIONS",
    "Access-Control-Allow-Headers" -> "Content-Type, Authorization",
    "Access-Control-Max-Age" -> "86400"
  )

  /**
   * Genera una respuesta JSON estandarizada con headers de CORS.
   * Evita el uso de decoradores que rompen los Path Parameters (:id).
   */
  def respond(data: JsValue, statusCode: Int = 200): cask.Response[String] = {
    cask.Response(
      data = data.toString(),
      statusCode = statusCode,
      headers = Seq("Content-Type" -> "application/json") ++ corsHeaders
    )
  }



  initialize()
}
