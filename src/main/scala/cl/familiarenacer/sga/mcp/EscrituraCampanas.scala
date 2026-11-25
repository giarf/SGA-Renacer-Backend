package cl.familiarenacer.sga.mcp

import cl.familiarenacer.sga.modelos._
import cl.familiarenacer.sga.modelos.CampanasJson._
import cl.familiarenacer.sga.repositorios.CampanaRepository
import play.api.libs.json._

private[mcp] object EscrituraCampanas {
  def herramientas(repo: CampanaRepository): List[Herramienta] = {
    val entero = Json.obj("type" -> "integer")
    val texto = Json.obj("type" -> "string")
    def lista(item: JsObject) = Json.obj("type" -> "array", "items" -> item)
    def objeto(campos: (String, JsObject)*): JsObject = esquema(campos, campos.map(_._1))
    def esquema(campos: Seq[(String, JsObject)], requeridos: Seq[String]): JsObject =
      Json.obj("type" -> "object", "properties" -> JsObject(campos), "required" -> requeridos, "additionalProperties" -> false)
    val opciones = "opciones" -> lista(objeto("id" -> texto, "nombre" -> texto))
    val id = "id" -> entero
    val participante = "participanteId" -> entero
    val columna = "columnaId" -> entero
    val version = "version" -> entero
    val nombre = "nombre" -> texto
    def num(a: JsObject, clave: String): Int = (a \ clave).as[Int]
    def ok = Json.obj("ok" -> true)
    def editar(nombre: String, descripcion: String, campos: Seq[(String, JsObject)], opcionales: Set[String] = Set.empty, destructiva: Boolean = false)(f: JsObject => JsValue): Herramienta = {
      val requeridos = campos.map(_._1).filterNot(opcionales)
      Herramienta(nombre, nombre.replace('_', ' '), descripcion, esquema(campos, requeridos), a => {
        if (requeridos.exists(k => !a.keys(k)) || !a.keys.subsetOf(campos.map(_._1).toSet))
          throw ErrorHerramienta("Revisa los argumentos requeridos y permitidos de la herramienta.")
        f(a)
      }, soloLectura = false, destructiva = destructiva)
    }
    List(
      editar("crear_campana", "Crea una campaña. Fecha YYYY-MM-DD; plantilla apadrinamiento o personalizada.", Seq(nombre, "fecha" -> texto, "descripcion" -> texto, "plantilla" -> texto), Set("descripcion", "plantilla"))(a => Json.obj("id" -> repo.crear(a.as[CrearCampana]))),
      editar("cambiar_estado_campana", "Cambia el estado a borrador, activa o cerrada. Usa la versión actual de la campaña.", Seq(id, version, "estado" -> texto))(a => { repo.cambiarEstado(num(a, "id"), a.as[CambiarEstadoCampana]); ok }),
      editar("agregar_participante_campana", "Agrega a una persona existente como beneficiaria.", Seq(id, "beneficiarioId" -> entero))(a => Json.obj("id" -> repo.agregarParticipante(num(a, "id"), a.as[RegistrarParticipante]))),
      editar("asignar_padrino_campana", "Asigna un padrino o usa null para quitarlo. Cambiarlo reinicia el seguimiento de comunicaciones asociado.", Seq(id, participante, version, "padrinoId" -> Json.obj("type" -> Json.arr("integer", "null"))), destructiva = true)(a => { repo.asignarPadrino(num(a, "id"), num(a, "participanteId"), a.as[AsignarPadrino]); ok }),
      editar("quitar_participante_campana", "Retira un participante y borra su seguimiento en esta campaña. Pide confirmación explícita.", Seq(id, participante), destructiva = true)(a => { repo.quitarParticipante(num(a, "id"), num(a, "participanteId")); ok }),
      editar("agregar_columna_campana", "Agrega una columna: boolean, text, number, ficha, whatsapp, select o rating. Ficha requiere campo y select requiere opciones con IDs únicos.", Seq(id, nombre, "tipo" -> texto, "campo" -> texto, opciones), Set("campo", "opciones"))(a => Json.obj("id" -> repo.agregarColumna(num(a, "id"), a.as[CrearColumnaCampana]))),
      editar("ordenar_columnas_campana", "Reordena todas las columnas. Envía todos los IDs exactamente una vez.", Seq(id, "columnas" -> lista(entero)))(a => { repo.ordenarColumnas(num(a, "id"), a.as[OrdenarColumnasCampana]); ok }),
      editar("eliminar_columna_campana", "Elimina una columna y todos sus valores. Pide confirmación explícita.", Seq(id, columna), destructiva = true)(a => { repo.quitarColumna(num(a, "id"), num(a, "columnaId")); ok }),
      editar("configurar_opciones_campana", "Reemplaza la lista de opciones. Conserva los IDs para renombrar sin perder asignaciones.", Seq(id, columna, version, opciones))(a => { repo.configurarOpciones(num(a, "id"), num(a, "columnaId"), a.as[ConfigurarOpcionesCampana]); ok }),
      editar("configurar_mensaje_campana", "Edita una plantilla WhatsApp y su destinatario (beneficiario o colaborador). Si cambia, reinicia las marcas de envío.", Seq(id, columna, version, "mensaje" -> texto, "destinatario" -> texto), destructiva = true)(a => { repo.configurarMensaje(num(a, "id"), num(a, "columnaId"), a.as[ConfigurarMensajeCampana]); ok }),
      editar("guardar_celda_campana", "Guarda una celda. Usa version 0 si aún no existe, e indica columnaVersion para select o whatsapp. Select usa el ID de la opción; rating usa un entero de 0 a 5. No envía WhatsApp.", Seq(id, participante, columna, version, "columnaVersion" -> entero, "valor" -> Json.obj()), Set("columnaVersion"), destructiva = true)(a => Json.toJson(repo.guardarCelda(num(a, "id"), num(a, "participanteId"), num(a, "columnaId"), a.as[GuardarCeldaCampana])))
    )
  }
}
