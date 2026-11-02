package cl.familiarenacer.sga.auth

import cask.model.{Request, Response}
import cask.router.Result
import cl.familiarenacer.sga.api.ApiSupport
import play.api.libs.json.Json

/**
 * Decorador global (SgaApiApp.mainDecorators): toda ruta exige un token válido de Authentik
 * salvo las públicas, y algunas exigen además el grupo de administración. Ver Autenticacion.requisito.
 */
class FiltroAutenticacion extends cask.RawDecorator with ApiSupport {

  def wrapFunction(ctx: Request, delegate: Delegate): Result[Response.Raw] = {
    val metodo = ctx.exchange.getRequestMethod.toString
    val ruta = ctx.exchange.getRequestPath
    val requisito = Autenticacion.requisito(metodo, ruta)

    if (Autenticacion.modo == "off" || requisito == Requisito.Publico) {
      delegate(ctx, Map())
    } else {
      val header = Option(ctx.exchange.getRequestHeaders.getFirst("Authorization"))
      val resultado: Either[(Int, String), UsuarioSesion] =
        Autenticacion.validar(header).left.map(401 -> _).flatMap { usuario =>
          Autenticacion.autorizar(usuario, requisito).map(403 -> _).toLeft(usuario)
        }

      resultado match {
        case Right(usuario) => Autenticacion.conUsuario(Some(usuario))(delegate(ctx, Map()))
        case Left((codigo, motivo)) if Autenticacion.modo == "log" =>
          println(s"[auth] (modo log) $metodo $ruta dejaría $codigo: $motivo")
          delegate(ctx, Map())
        case Left((codigo, motivo)) =>
          Result.Success(respond(Json.obj("error" -> motivo), codigo))
      }
    }
  }
}
