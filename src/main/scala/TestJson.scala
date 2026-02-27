import cl.familiarenacer.sga.api.routes.IngresosRoutes
import cl.familiarenacer.sga.modelos._
import play.api.libs.json._

object TestJson extends App {
  implicit val ingresoFormat: OFormat[IngresoRecurso] = Json.format[IngresoRecurso]
  implicit val donacionFormat: OFormat[IngresoDonacion] = Json.format[IngresoDonacion]
  implicit val pecuniarioFormat: OFormat[IngresoPecuniario] = Json.format[IngresoPecuniario]

  case class DonacionRequest(ingreso: IngresoRecurso, donacion: IngresoDonacion, pecuniario: IngresoPecuniario)
  implicit val donacionRequestFormat: OFormat[DonacionRequest] = Json.format[DonacionRequest]

  val jsonString = """{
  "ingreso": {
    "origenEntidadId": 45,
    "responsableInternoId": 45,
    "montoTotal": 50000,
    "tipoTransaccion": "Donacion",
    "estado": "Cerrado",
    "anotaciones": "Colecta municipal julio"
  },
  "donacion": {
    "ingresoId": 0,
    "propositoEspecifico": "Programa Invierno",
    "gestorId": 5
  },
  "pecuniario": {
    "ingresoId": 0,
    "cuentaDestinoId": 10,
    "metodoTransferencia": "Transferencia"
  }
}"""

  val json = Json.parse(jsonString)
  try {
    val req = json.as[DonacionRequest]
    println("DESERIALIZATION SUCCESS: " + req)
  } catch {
    case e: Exception =>
      println("DESERIALIZATION FAILED: " + e.getMessage)
      e.printStackTrace()
  }
}
