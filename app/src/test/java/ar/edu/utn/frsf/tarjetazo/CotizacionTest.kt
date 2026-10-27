package ar.edu.utn.frsf.tarjetazo

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class CotizacionTest {
    @Test
    fun pasaDolaresAPesosSinPerderCentavos() {
        assertEquals(BigDecimal("40560.00"), Cotizacion.aPesos(BigDecimal("20"), BigDecimal("2028")))
        assertEquals(BigDecimal("25.36"), Cotizacion.aPesos(BigDecimal("0.0125"), BigDecimal("2028.5")))
    }
}
