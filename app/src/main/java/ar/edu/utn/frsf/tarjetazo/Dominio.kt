package ar.edu.utn.frsf.tarjetazo

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

// Reglas del negocio, sin nada de Android: se prueban en DominioTest sin emulador.

data class Gasto(
    val id: Long,
    val fecha: String,
    val comercio: String,
    val importe: BigDecimal,
    val division: List<Pair<String, Int>>,
)

const val TITULAR = "Titular"

private val CIEN = BigDecimal(100)

/** "10.000,50" o "10000.50". Null si no es un importe positivo con a lo sumo dos decimales. */
fun parseImporte(texto: String): BigDecimal? {
    val t = texto.trim().let { if (',' in it) it.replace(".", "").replace(',', '.') else it }
    val n = t.toBigDecimalOrNull() ?: return null
    return if (n.signum() > 0 && n.scale() <= 2) n.setScale(2) else null
}

/**
 * Una persona por línea, con el formato "Nombre: porcentaje".
 * Vacío significa que el gasto es entero del titular (RF-3).
 * Lanza IllegalArgumentException con el mensaje para mostrarle al usuario (RF-2).
 */
fun parseDivision(texto: String): List<Pair<String, Int>> {
    val lineas = texto.lines().map { it.trim() }.filter { it.isNotEmpty() }
    if (lineas.isEmpty()) return listOf(TITULAR to 100)

    val division = lineas.map { linea ->
        val partes = linea.split(':')
        val nombre = partes[0].trim()
        val porcentaje = partes.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
        require(partes.size == 2 && nombre.isNotEmpty() && porcentaje > 0) {
            "Línea inválida: \"$linea\". Usá Nombre: porcentaje"
        }
        nombre to porcentaje
    }
    require(division.map { it.first.lowercase() }.distinct().size == division.size) { "Hay una persona repetida" }
    val suma = division.sumOf { it.second }
    require(suma == 100) { "Los porcentajes suman $suma y tienen que sumar 100" }
    return division
}

/**
 * Parte de cada persona en un gasto. Cada parte se trunca al centavo y lo que sobra
 * se le suma a la primera persona, así la suma da siempre el importe exacto (RNF-3).
 */
fun repartir(gasto: Gasto): List<Pair<String, BigDecimal>> {
    val partes = gasto.division.map { (nombre, porcentaje) ->
        nombre to gasto.importe.multiply(BigDecimal(porcentaje)).divide(CIEN, 2, RoundingMode.DOWN)
    }
    val resto = gasto.importe - partes.sumOf { it.second }
    return partes.mapIndexed { i, (nombre, parte) -> nombre to if (i == 0) parte + resto else parte }
}

/** Cuánto debe cada persona sumando su parte de todos los gastos (RF-6). */
fun deudas(gastos: List<Gasto>): Map<String, BigDecimal> {
    val total = linkedMapOf<String, BigDecimal>()
    gastos.flatMap(::repartir).forEach { (nombre, parte) -> total[nombre] = (total[nombre] ?: BigDecimal.ZERO) + parte }
    return total
}

fun moneda(n: BigDecimal): String = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-AR")).format(n)

fun resumen(deudas: Map<String, BigDecimal>): String =
    if (deudas.isEmpty()) "Todavía no hay gastos cargados."
    else "Tarjetazo — resumen de deudas\n\n" + deudas.entries.joinToString("\n") { "${it.key}: ${moneda(it.value)}" }
