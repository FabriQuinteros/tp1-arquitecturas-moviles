package ar.edu.utn.frsf.tarjetazo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MonetizacionTest {
    private val ahora = 1_800_000_000_000L

    @Test
    fun laCompraEnElTelefonoAlcanzaAunqueElBackendNoHayaRespondido() {
        assertTrue(plusActivo(servidor = null, compraEnElTelefono = true, ahora = ahora))
    }

    @Test
    fun elBackendDaPlusEnOtroTelefonoMientrasNoVenza() {
        assertTrue(plusActivo(PlusServidor(activo = true, venceMillis = ahora + 1), false, ahora))
        assertFalse(plusActivo(PlusServidor(activo = true, venceMillis = ahora), false, ahora))
    }

    @Test
    fun sinCompraNiBackendNoHayPlus() {
        assertFalse(plusActivo(null, false, ahora))
        assertFalse(plusActivo(PlusServidor(activo = false, venceMillis = ahora + 1), false, ahora))
    }

    @Test
    fun ahorroDelPlanAnual() {
        // $1.999 por mes contra $14.999 por año: doce meses son $23.988.
        assertEquals(37, ahorroAnual(1_999_000_000, 14_999_000_000))
        assertEquals(0, ahorroAnual(1_999_000_000, 30_000_000_000))
        assertEquals(0, ahorroAnual(0, 14_999_000_000))
    }

    @Test
    fun duracionDeLaPrueba() {
        assertEquals("14 días", duracion("P14D"))
        assertEquals("1 semana", duracion("P1W"))
        assertEquals("1 mes", duracion("P1M"))
        assertEquals("1 año", duracion("P1Y"))
        assertNull(duracion("P1M2D"))
    }
}
