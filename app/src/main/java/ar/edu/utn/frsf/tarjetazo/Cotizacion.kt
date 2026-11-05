package ar.edu.utn.frsf.tarjetazo

import com.google.firebase.firestore.FirebaseFirestore
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * El "dólar tarjeta" (ya incluye los impuestos que el banco suma a los consumos en dólares).
 * La app no llama a DolarApi: una tarea programada del backend lo consulta cada 30 minutos y lo deja
 * en `cotizaciones/tarjeta`. Así una sola llamada sirve a todos los usuarios, y sin conexión
 * Firestore responde con el último valor guardado en el teléfono.
 */
object Cotizacion {
    fun dolarTarjeta(listo: (Result<BigDecimal>) -> Unit) {
        FirebaseFirestore.getInstance().document("cotizaciones/tarjeta").get()
            .addOnSuccessListener { doc -> listo(runCatching { BigDecimal(doc.getString("venta")!!) }) }
            .addOnFailureListener { listo(Result.failure(it)) }
    }

    fun aPesos(dolares: BigDecimal, cotizacion: BigDecimal): BigDecimal =
        dolares.multiply(cotizacion).setScale(2, RoundingMode.HALF_UP)
}
