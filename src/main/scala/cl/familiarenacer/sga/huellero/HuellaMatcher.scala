package cl.familiarenacer.sga.huellero

import cl.familiarenacer.sga.modelos._
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.cbor.CBORFactory
import com.machinezoo.sourceafis.{FingerprintImage, FingerprintImageOptions, FingerprintMatcher, FingerprintTemplate}
import java.util.stream.IntStream

/** Plantilla extraída de una imagen: lista para comparar y para guardar en Postgres. */
case class PlantillaExtraida(template: FingerprintTemplate, bytes: Array[Byte], minucias: Int)
case class Coincidencia(huellaId: Int, personaId: Int, puntaje: Double, segundaPersona: Double)

object HuellaMatcher {
  val Version = "3.18.1"
  val Formato = s"sourceafis-$Version"
  private val cbor = new ObjectMapper(new CBORFactory())

  /** SourceAFIS no expone las minucias; la plantilla serializada (CBOR) trae una letra por minucia en "types". */
  def minucias(bytes: Array[Byte]): Int =
    scala.util.Try(Option(cbor.readTree(bytes).get("types")).map(_.asText().length).getOrElse(0)).getOrElse(0)

  def extraer(png: Array[Byte], dpi: Double): PlantillaExtraida = {
    val template = try new FingerprintTemplate(new FingerprintImage(png, new FingerprintImageOptions().dpi(dpi)))
    catch { case _: IllegalArgumentException => throw HuellaError(502, "El huellero devolvió una imagen que no se pudo leer.") }
    val bytes = template.toByteArray()
    PlantillaExtraida(template, bytes, minucias(bytes))
  }
}

/**
 * Caché en memoria de todas las tomas registradas y búsqueda 1:N con SourceAFIS.
 * El puntaje de una persona es el mejor entre todas sus tomas y dedos.
 */
class HuellaMatcher(
  val umbral: Double = sys.env.get("HUELLA_UMBRAL").flatMap(_.toDoubleOption).getOrElse(40.0),
  val margen: Double = sys.env.get("HUELLA_MARGEN").flatMap(_.toDoubleOption).getOrElse(0.0),
  val minimoMinucias: Int = sys.env.get("HUELLA_MINUCIAS_MIN").flatMap(_.toIntOption).getOrElse(15)
) {
  private case class Entrada(huellaId: Int, personaId: Int, toma: Int, template: FingerprintTemplate)

  // Copia inmutable reemplazada en cada cambio: las lecturas no se bloquean.
  @volatile private var entradas: Array[Entrada] = Array.empty

  private def entrada(t: TomaGuardada) = Entrada(t.huellaId, t.personaId, t.toma, new FingerprintTemplate(t.plantilla))

  def cargar(tomas: Seq[TomaGuardada]): Unit = { val nuevas = tomas.map(entrada).toArray; synchronized { entradas = nuevas } }

  def reemplazar(huellaId: Int, tomas: Seq[TomaGuardada]): Unit = {
    val nuevas = tomas.map(entrada)
    synchronized { entradas = entradas.filterNot(_.huellaId == huellaId) ++ nuevas }
  }

  def quitar(huellaId: Int): Unit = synchronized { entradas = entradas.filterNot(_.huellaId == huellaId) }

  def vacio: Boolean = entradas.isEmpty
  def total: Int = entradas.length

  /** Mejor coincidencia sobre el umbral; None si nadie lo supera o si dos personas quedan demasiado cerca. */
  def identificar(probe: FingerprintTemplate): Option[Coincidencia] = mejor(probe)
    .filter(c => c.puntaje >= umbral && c.puntaje - c.segundaPersona >= margen)

  /** Mejor puntaje sin aplicar el umbral (para logs y calibración). */
  def mejor(probe: FingerprintTemplate): Option[Coincidencia] = {
    val actuales = entradas
    if (actuales.isEmpty) return None
    val matcher = new FingerprintMatcher(probe)
    // match() es seguro entre hilos (estado por hilo en SourceAFIS 3.x).
    val puntajes = IntStream.range(0, actuales.length).parallel().mapToDouble(i => matcher.`match`(actuales(i).template)).toArray
    val porPersona = actuales.indices.groupBy(i => actuales(i).personaId).view
      .mapValues(is => is.maxBy(puntajes(_))).toSeq.sortBy { case (_, i) => -puntajes(i) }
    porPersona.headOption.map { case (personaId, i) =>
      Coincidencia(actuales(i).huellaId, personaId, puntajes(i), porPersona.lift(1).map { case (_, j) => puntajes(j) }.getOrElse(0.0))
    }
  }

  /** Calienta la JVM para que el primer toque del día no pague la compilación JIT. */
  def calentar(): Unit = scala.util.Try {
    val ancho = 384; val alto = 290
    val pixeles = Array.tabulate[Byte](ancho * alto) { i =>
      val x = i % ancho; val y = i / ancho
      val d = math.hypot(x - ancho / 2.0, y - alto / 2.0)
      if (math.sin(d / 3.5) > 0) 40.toByte else 220.toByte
    }
    val t = new FingerprintTemplate(new FingerprintImage(ancho, alto, pixeles, new FingerprintImageOptions().dpi(500)))
    (1 to 50).foreach(_ => new FingerprintMatcher(t).`match`(t))
  }
}
