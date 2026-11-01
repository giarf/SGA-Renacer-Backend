package cl.familiarenacer.sga

import cl.familiarenacer.sga.huellero.HuellaMatcher
import cl.familiarenacer.sga.modelos.TomaGuardada
import org.scalatest.funsuite.AnyFunSuite

class HuellaMatcherSpec extends AnyFunSuite {
  private def imagen(nombre: String) = getClass.getResourceAsStream(s"/huellas/$nombre.png").readAllBytes()
  private lazy val probe = HuellaMatcher.extraer(imagen("probe"), 500)
  private lazy val matching = HuellaMatcher.extraer(imagen("matching"), 500)
  private lazy val nonmatching = HuellaMatcher.extraer(imagen("nonmatching"), 500)

  test("extrae plantillas con minucias desde PNG") {
    assert(probe.minucias > 15)
    assert(HuellaMatcher.minucias(probe.bytes) == probe.minucias)
  }

  test("identifica a la persona correcta con el mejor puntaje y aplica el umbral") {
    val m = new HuellaMatcher(umbral = 40, margen = 0, minimoMinucias = 15)
    m.cargar(Seq(TomaGuardada(10, 1, 1, matching.bytes, matching.minucias), TomaGuardada(20, 2, 1, nonmatching.bytes, nonmatching.minucias)))
    val c = m.identificar(probe.template).getOrElse(fail("no identificó"))
    assert(c.personaId == 1 && c.huellaId == 10 && c.puntaje >= 40 && c.segundaPersona < 40)
    m.quitar(10)
    assert(m.identificar(probe.template).isEmpty)
    assert(m.mejor(probe.template).exists(_.personaId == 2))
  }

  test("el margen descarta dos personas demasiado parecidas") {
    val m = new HuellaMatcher(umbral = 40, margen = 1e9, minimoMinucias = 15)
    m.cargar(Seq(TomaGuardada(10, 1, 1, matching.bytes, matching.minucias), TomaGuardada(20, 2, 1, nonmatching.bytes, nonmatching.minucias)))
    assert(m.identificar(probe.template).isEmpty)
  }

  test("búsqueda 1:N sobre 3000 tomas en memoria") {
    val m = new HuellaMatcher()
    val otras = (1 to 2999).map(i => TomaGuardada(i, 1000 + i, 1, nonmatching.bytes, nonmatching.minucias))
    m.cargar(otras :+ TomaGuardada(5000, 7, 1, matching.bytes, matching.minucias))
    m.calentar()
    (1 to 3).foreach(_ => m.identificar(probe.template))
    val inicio = System.nanoTime()
    val c = m.identificar(probe.template)
    val ms = (System.nanoTime() - inicio) / 1e6
    info(f"3000 tomas: $ms%.1f ms con ${Runtime.getRuntime.availableProcessors} núcleos")
    assert(c.exists(_.personaId == 7))
  }
}
