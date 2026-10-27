package ar.edu.utn.frsf.tarjetazo

import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.HttpURLConnection
import java.net.URL

/**
 * API REST de DolarApi (https://dolarapi.com): sin clave ni SDK, se consume con HTTP y JSON
 * de la biblioteca estándar. El "dólar tarjeta" ya incluye los impuestos que el banco suma
 * a los consumos en dólares, así que es el valor que va a aparecer en el resumen.
 * Bloquea: llamar fuera del hilo principal.
 */
object Cotizacion {
    private const val URL_TARJETA = "https://dolarapi.com/v1/dolares/tarjeta"

    fun dolarTarjeta(): BigDecimal {
        val conexion = URL(URL_TARJETA).openConnection() as HttpURLConnection
        conexion.connectTimeout = 5_000
        conexion.readTimeout = 5_000
        try {
            if (conexion.responseCode != 200) error("La cotización respondió ${conexion.responseCode}")
            val json = JSONObject(conexion.inputStream.bufferedReader().use { it.readText() })
            return BigDecimal(json.getString("venta"))
        } finally {
            conexion.disconnect()
        }
    }

    fun aPesos(dolares: BigDecimal, cotizacion: BigDecimal): BigDecimal =
        dolares.multiply(cotizacion).setScale(2, RoundingMode.HALF_UP)
}
