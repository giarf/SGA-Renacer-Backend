package cl.familiarenacer.sga.api

import cask.model.Response
import cl.familiarenacer.sga.modelos._
import cl.familiarenacer.sga.repositorios.{DB, DonacionRepository, EntidadRepository, InventarioRepository}
import play.api.libs.json._

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

  // Formatos JSON Implicítos para los modelos
  implicit val entidadFormat: OFormat[Entidad] = Json.format[Entidad]
  implicit val personaFormat: OFormat[PersonaNatural] = Json.format[PersonaNatural]
  implicit val ingresoFormat: OFormat[IngresoRecurso] = Json.format[IngresoRecurso]
  implicit val donacionFormat: OFormat[IngresoDonacion] = Json.format[IngresoDonacion]
  implicit val pecuniarioFormat: OFormat[IngresoPecuniario] = Json.format[IngresoPecuniario]
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
    nombres: String,
    apellidos: Option[String],
    genero: Option[String]
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
    nombres: String,
    apellidos: Option[String],
    genero: Option[String]
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
   * Endpoint: Obtener Persona por ID
   * GET /api/personas?id=3
   * Retorna los datos completos de una persona (Entidad + PersonaNatural).
   */
  @cask.get("/api/personas")
  def obtenerPersona(id: Int) = {
    println(s"[DEBUG] Obteniendo persona con ID: $id")
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
            nombres = persona.nombres,
            apellidos = persona.apellidos,
            genero = persona.genero
          )
          cask.Response(Json.toJson(response).toString(), 200, headers = Seq("Content-Type" -> "application/json"))
        case None =>
          cask.Response(Json.obj("error" -> s"Persona con ID $id no encontrada").toString(), 404, headers = Seq("Content-Type" -> "application/json"))
      }
    } catch {
      case e: Exception =>
        e.printStackTrace()
        cask.Response(Json.obj("error" -> e.getMessage).toString(), 500, headers = Seq("Content-Type" -> "application/json"))
    }
  }


  /**
   * Endpoint: OPTIONS para CORS Preflight - Editar Persona
   * OPTIONS /api/personas/editar
   */
  @cask.options("/api/personas/editar")
  def editarPersonaOptions() = {
    cask.Response(
      data = "",
      statusCode = 204,
      headers = corsHeaders
    )
  }

  /**
   * Endpoint: Editar Persona Natural
   * PUT /api/personas/editar
   * Actualiza datos de una persona y su entidad base.
   */
  @cask.put("/api/personas/editar")
  def editarPersona(request: cask.Request) = {
    try {
      val body = Json.parse(request.text()).as[EditarPersonaRequest]
      
      // Crear objetos para actualizar
      val entidad = Entidad(
        id = body.id,
        rut = body.rut,
        tipoEntidad = Some(body.tipoEntidad),
        telefono = body.telefono,
        correo = body.correo,
        direccion = body.direccion,
        comuna = body.comuna,
        createdAt = None  // No actualizamos createdAt
      )
      
      val persona = PersonaNatural(
        entidadId = body.id,
        nombres = body.nombres,
        apellidos = body.apellidos,
        genero = body.genero
      )
      
      val rowsUpdated = entidadRepo.actualizarPersonaNatural(body.id, persona, entidad)
      
      if (rowsUpdated > 0) {
        cask.Response(
          Json.obj("mensaje" -> "Persona actualizada exitosamente", "filasActualizadas" -> rowsUpdated).toString(),
          200,
          headers = Seq("Content-Type" -> "application/json")
        )
      } else {
        cask.Response(
          Json.obj("error" -> s"No se encontró la persona con ID ${body.id}").toString(),
          404,
          headers = Seq("Content-Type" -> "application/json")
        )
      }
    } catch {
      case e: Exception =>
        e.printStackTrace()
        cask.Response(
          Json.obj("error" -> e.getMessage).toString(),
          500,
          headers = Seq("Content-Type" -> "application/json")
        )
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

  // Estructura auxiliar para request de actualización completa de Persona
  case class ActualizarPersonaRequest(
    id: Int,
    rut: Option[String],
    tipoEntidad: Option[String],
    telefono: Option[String],
    correo: Option[String],
    direccion: Option[String],
    comuna: Option[String],
    nombres: String,
    apellidos: Option[String],
    genero: Option[String]
  )
  implicit val actualizarPersonaFormat = Json.format[ActualizarPersonaRequest]

  // Estructura para capturar los datos de una nueva persona
  case class RegistrarPersonaRequest(
    rut: Option[String],
    tipoEntidad: Option[String],
    telefono: Option[String],
    correo: Option[String],
    direccion: Option[String],
    comuna: Option[String],
    nombres: String,
    apellidos: Option[String],
    genero: Option[String]
  )
  implicit val registrarPersonaFormat = Json.format[RegistrarPersonaRequest]

  /**
   * Endpoint: Registrar Nueva Persona
   * POST /api/entidades/registrar
   */
  @cask.post("/api/entidades/registrar")
  def registrarPersona(request: cask.Request) = {
    try {
      val body = Json.parse(request.text()).as[RegistrarPersonaRequest]

      // 1. Construimos el objeto Entidad (sin ID, porque lo genera la DB)
      val nuevaEntidad = Entidad(
        id = 0, // Quill/Postgres ignorará esto al ser auto-incremental
        rut = body.rut,
        tipoEntidad = body.tipoEntidad.orElse(Some("Persona")),
        telefono = body.telefono,
        correo = body.correo,
        direccion = body.direccion,
        comuna = body.comuna,
        createdAt = Some(java.time.LocalDateTime.now())
      )

      // 2. Construimos el objeto Persona
      val nuevaPersona = PersonaNatural(
        entidadId = 0, // Se llenará con el ID generado de la entidad
        nombres = body.nombres,
        apellidos = body.apellidos,
        genero = body.genero
      )

      // 3. Llamada al repositorio (debe ser transaccional)
      // Corregido orden de argumentos: (persona, entidad)
      val idGenerado = entidadRepo.registrarPersonaNatural(nuevaPersona, nuevaEntidad)

      cask.Response(
        Json.obj("mensaje" -> "Persona creada exitosamente", "id" -> idGenerado).toString(),
        201, 
        headers = Seq("Content-Type" -> "application/json")
      )
    } catch {
      case e: Exception =>
        cask.Response(Json.obj("error" -> e.getMessage).toString(), 500)
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
        createdAt = None
      )

      val persona = PersonaNatural(
        entidadId = body.id,
        nombres = body.nombres,
        apellidos = body.apellidos,
        genero = body.genero
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

  // Decorador para agregar CORS headers a todas las respuestas
  override def mainDecorators = Seq(new CorsDecorator())

  class CorsDecorator extends cask.RawDecorator {
    def wrapFunction(ctx: cask.Request, delegate: Delegate) = {
      delegate(Map()).map(r => r.copy(headers = r.headers ++ corsHeaders))
    }
  }

  initialize()
}
