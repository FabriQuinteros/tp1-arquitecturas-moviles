package ar.edu.utn.frsf.tarjetazo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

class RetencionTest {
    private val hoy = LocalDate.parse("2026-11-12") // jueves

    private fun dias(vararg f: String) = f.map(LocalDate::parse)

    @Test
    fun rachaDeSemanasSeguidas() {
        // Esta semana, la anterior y la otra: 3. La de hace un mes no cuenta porque hay un hueco.
        assertEquals(3, racha(dias("2026-11-10", "2026-11-03", "2026-10-26", "2026-10-05"), hoy))
    }

    @Test
    fun laRachaSigueVivaHastaElDomingo() {
        // Esta semana todavía no cargó nada, pero las dos anteriores sí.
        assertEquals(2, racha(dias("2026-11-06", "2026-10-28"), hoy))
        // Si la semana pasada tampoco, se cortó.
        assertEquals(0, racha(dias("2026-10-28"), hoy))
        assertEquals(0, racha(emptyList(), hoy))
    }

    @Test
    fun variosGastosEnLaMismaSemanaCuentanUnaVez() {
        assertEquals(1, racha(dias("2026-11-09", "2026-11-11", "2026-11-12"), hoy))
    }

    @Test
    fun resumenDelMesConQuienMasDebe() {
        val gastos = listOf(
            Gasto(1, "2026-10-05", "Súper", BigDecimal("30000.00"), parseDivision("Ana: 50\nTitular: 50")),
            Gasto(2, "2026-10-20", "Nafta", BigDecimal("10000.00"), parseDivision("Beto: 100")),
            Gasto(3, "2026-11-01", "Otro mes", BigDecimal("999.00"), parseDivision("")),
        )
        val r = resumenDelMes(gastos, YearMonth.of(2026, 10))
        assertEquals(2, r.cantidad)
        assertEquals(BigDecimal("40000.00"), r.total)
        assertEquals("Ana" to BigDecimal("15000.00"), r.quienMasDebe)
        assertEquals("Octubre: ${moneda(BigDecimal("40000.00"))} en 2 gastos · Ana te debe ${moneda(BigDecimal("15000.00"))}", textoResumen(r, YearMonth.of(2026, 10)))
    }

    @Test
    fun mesSinGastosNoMuestraResumen() {
        assertNull(textoResumen(resumenDelMes(emptyList(), YearMonth.of(2026, 10)), YearMonth.of(2026, 10)))
    }

    @Test
    fun diasHastaElCierre() {
        assertEquals(3, diasHastaCierre(15, hoy))
        assertEquals(0, diasHastaCierre(12, hoy))
        assertEquals(29, diasHastaCierre(11, hoy)) // ya pasó: el próximo es el 11 de diciembre
        assertEquals(18, diasHastaCierre(31, hoy)) // noviembre tiene 30 días
    }
}
