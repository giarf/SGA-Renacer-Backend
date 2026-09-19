package cl.familiarenacer.sga

import cl.familiarenacer.sga.modelos._
import org.scalatest.funsuite.AnyFunSuite
import play.api.libs.json._

class CampanaMensajeSpec extends AnyFunSuite {
  test("mensajes libres, enviados y desmarcados tienen un formato válido") {
    Seq(false, true).foreach(e => CampanaMensaje.validar(Json.obj("texto" -> "Hola {nombre}", "enviado" -> e)))
    CampanaMensaje.validar(Json.obj("texto" -> "", "enviado" -> false))
  }
  test("rechaza mensajes vacíos enviados, tipos incorrectos y exceso de longitud") {
    Seq(JsNull, JsString("hola"), Json.obj("texto" -> "hola"),
      Json.obj("texto" -> " ", "enviado" -> true), Json.obj("texto" -> "hola", "enviado" -> "sí"),
      Json.obj("texto" -> ("a" * 2001), "enviado" -> false)).foreach { value =>
      assert(intercept[CampanaError](CampanaMensaje.validar(value)).status == 400)
    }
  }
}
