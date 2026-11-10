package ar.edu.utn.frsf.tarjetazo

import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

// Reglas de las estrategias de retención, sin nada de Android: se prueban en RetencionTest.
// El backend tiene las mismas reglas en backend/retencion.py para armar los avisos.

/** A qué pantalla lleva un aviso al tocarlo. Viaja como dato en la notificación. */
const val DESTINO = "destino"
const val DESTINO_NUEVO_GASTO = "nuevo_gasto"
const val DESTINO_DEUDAS = "deudas"

/**
 * Semanas seguidas (de lunes a domingo) con al menos un gasto cargado, contando hacia atrás.
 * Si esta semana todavía no hay gastos, la racha sigue viva desde la anterior: el usuario
 * tiene hasta el domingo para no perderla.
 */
fun racha(fechas: List<LocalDate>, hoy: LocalDate): Int {
    val lunes = { d: LocalDate -> d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
    val semanas = fechas.filter { !it.isAfter(hoy) }.map(lunes).toSet()
    var semana = lunes(hoy)
    if (semana !in semanas) semana = semana.minusWeeks(1)
    var cuenta = 0
    while (semana in semanas) {
        cuenta++
        semana = semana.minusWeeks(1)
    }
    return cuenta
}

data class ResumenMes(val cantidad: Int, val total: BigDecimal, val quienMasDebe: Pair<String, BigDecimal>?)

/** Lo que se gastó en un mes y quién le debe más al titular, para el contenido personalizado. */
fun resumenDelMes(gastos: List<Gasto>, mes: YearMonth): ResumenMes {
    val delMes = gastos.filter { YearMonth.from(LocalDate.parse(it.fecha)) == mes }
    val otros = deudas(delMes).filterKeys { it != TITULAR }
    return ResumenMes(delMes.size, delMes.sumOf { it.importe }, otros.maxByOrNull { it.value }?.toPair())
}

/** "Noviembre: $ 45.000,00 en 12 gastos · Ana te debe $ 15.000,00". Null si en el mes no hubo gastos. */
fun textoResumen(r: ResumenMes, mes: YearMonth): String? {
    if (r.cantidad == 0) return null
    val nombre = mes.month.getDisplayName(TextStyle.FULL, Locale.forLanguageTag("es-AR")).replaceFirstChar { it.uppercase() }
    val gastos = if (r.cantidad == 1) "1 gasto" else "${r.cantidad} gastos"
    return "$nombre: ${moneda(r.total)} en $gastos" + (r.quienMasDebe?.let { " · ${it.first} te debe ${moneda(it.second)}" } ?: "")
}

fun textoRacha(semanas: Int): String? = when {
    semanas <= 0 -> null
    semanas == 1 -> "Esta semana ya anotaste gastos. Seguí así la próxima."
    else -> "Llevás $semanas semanas seguidas anotando tus gastos."
}

/** Días hasta el próximo cierre. Un día de cierre 31 en un mes de 30 días cae el último día del mes. */
fun diasHastaCierre(diaCierre: Int, hoy: LocalDate): Long {
    fun cierreEn(mes: YearMonth) = mes.atDay(minOf(diaCierre, mes.lengthOfMonth()))
    val este = cierreEn(YearMonth.from(hoy))
    val proximo = if (este.isBefore(hoy)) cierreEn(YearMonth.from(hoy).plusMonths(1)) else este
    return ChronoUnit.DAYS.between(hoy, proximo)
}
