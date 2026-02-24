import cl.familiarenacer.sga.api.routes.IngresosRoutes
import cl.familiarenacer.sga.modelos._
import play.api.libs.json._
import cl.familiarenacer.sga.repositorios._

object TestDb extends App {
  implicit val ingresoFormat: OFormat[IngresoRecurso] = Json.format[IngresoRecurso]
  implicit val donacionFormat: OFormat[IngresoDonacion] = Json.format[IngresoDonacion]
  implicit val pecuniarioFormat: OFormat[IngresoPecuniario] = Json.format[IngresoPecuniario]

  case class DonacionRequest(ingreso: IngresoRecurso, donacion: IngresoDonacion, pecuniario: IngresoPecuniario)
  implicit val donacionRequestFormat: OFormat[DonacionRequest] = Json.format[DonacionRequest]

  val jsonString = """{
  "ingreso": {
    "id": 0,
    "origenEntidadId": 45,
    "responsableInternoId": 45,
    "montoTotal": 50000,
    "tipoTransaccion": "Donacion",
    "estado": "Cerrado"
  },
  "donacion": {
    "ingresoId": 0,
    "numeroCertificado": "DON-TEST-1234",
    "propositoEspecifico": "Programa Invierno"
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
    val repo = new DonacionRepository(DB.ctx)
    val id = repo.registrarDonacionDinero(req.ingreso, req.donacion, req.pecuniario)
    println(s"SUCCESSFULLY INSERTED! ID: $id")
  } catch {
    case e: Exception =>
      println("DB INSERTION FAILED:")
      e.printStackTrace()
  }
}
