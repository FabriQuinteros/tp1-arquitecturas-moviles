package ar.edu.utn.frsf.tarjetazo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class DominioTest {
    private fun gasto(importe: String, division: String) =
        Gasto(1, "2026-09-13", "Comercio", parseImporte(importe)!!, parseDivision(division))

    @Test
    fun elCentavoQueSobraVaALaPrimeraPersona() {
        assertEquals(
            listOf("Ana" to BigDecimal("5000.01"), "Beto" to BigDecimal("5000.00")),
            repartir(gasto("10.000,01", "Ana: 50\nBeto: 50")),
        )
    }

    @Test
    fun lasPartesSumanExactoElImporte() {
        val partes = repartir(gasto("0,10", "A: 33\nB: 33\nC: 34"))
        assertEquals(BigDecimal("0.10"), partes.sumOf { it.second })
        assertEquals(BigDecimal("0.04"), partes[0].second)
    }

    @Test
    fun sinDivisionTodoEsDelTitular() {
        assertEquals(listOf(TITULAR to 100), parseDivision("  \n "))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rechazaPorcentajesQueNoSuman100() {
        parseDivision("Ana: 50\nBeto: 40")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rechazaPersonasRepetidas() {
        parseDivision("Ana: 50\nana: 50")
    }

    @Test
    fun importes() {
        assertEquals(BigDecimal("10000.50"), parseImporte("10.000,50"))
        assertEquals(BigDecimal("10000.50"), parseImporte("10000.50"))
        assertNull(parseImporte("-5"))
        assertNull(parseImporte("1,234"))
        assertNull(parseImporte("abc"))
    }

    @Test
    fun lasDeudasSeAcumulanPorPersona() {
        val gastos = listOf(gasto("100", "Ana: 50\nBeto: 50"), gasto("40", "Ana: 100"))
        assertEquals(mapOf("Ana" to BigDecimal("90.00"), "Beto" to BigDecimal("50.00")), deudas(gastos))
    }
}
