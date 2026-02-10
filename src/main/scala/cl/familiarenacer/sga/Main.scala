package cl.familiarenacer.sga

import cl.familiarenacer.sga.repositorios.{DB, EntidadRepository}

object Main extends App {
  println("🚀 Iniciando SGA Renacer Backend...")

  // Inicializamos los repositorios con el contexto de DB
  val entidadRepo = new EntidadRepository(DB.ctx)

  println("📊 Listando personas registradas...")
  try {
    val personas = entidadRepo.listarPersonas()
    if (personas.isEmpty) {
      println("⚠️ No hay personas registradas en la base de datos.")
    } else {
      personas.foreach { p =>
        println(s"👤 Persona: ${p.nombres} ${p.apellidos.getOrElse("")}")
      }
    }
  } catch {
    case e: Exception =>
      println("❌ Error al acceder a la base de datos.")
      e.printStackTrace()
  } finally {
    try {
      DB.ctx.close()
    } catch {
      case _: Throwable => // Ignorar errores al cerrar si ya estaba cerrado
    }
  }
}
