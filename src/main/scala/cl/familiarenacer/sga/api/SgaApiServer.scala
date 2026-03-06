package cl.familiarenacer.sga.api

import java.util.concurrent.CountDownLatch

/**
 * Launcher bloqueante para ejecutar la API en procesos batch (Docker/CI).
 * Cask inicia Undertow y retorna inmediatamente, por lo que aquí mantenemos
 * el proceso vivo hasta recibir señal de término.
 */
object SgaApiServer {
  def main(args: Array[String]): Unit = {
    SgaApiApp.main(args)
    try {
      new CountDownLatch(1).await()
    } catch {
      case _: InterruptedException =>
        Thread.currentThread.interrupt()
    }
  }
}
