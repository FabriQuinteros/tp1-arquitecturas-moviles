package ar.edu.utn.frsf.tarjetazo

// Reglas del plan Plus, sin nada de Android: se prueban en MonetizacionTest sin emulador.

/** Id de la suscripción en Google Play Console y de sus dos planes base. */
const val PRODUCTO_PLUS = "plus"
const val PLAN_MENSUAL = "mensual"
const val PLAN_ANUAL = "anual"

/** Lo que el backend sabe del plan, en `usuarios/{uid}.plus`. Solo lo escribe el backend. */
data class PlusServidor(val activo: Boolean, val venceMillis: Long)

/**
 * Plus vale si Google Play informa una compra activa en este teléfono (respuesta inmediata
 * después de pagar, aun sin backend) o si el backend lo da por vigente (sigue a la cuenta en
 * cualquier teléfono). Se exige además que no esté vencido, por si el último aviso de Play
 * todavía no llegó.
 */
fun plusActivo(servidor: PlusServidor?, compraEnElTelefono: Boolean, ahora: Long): Boolean =
    compraEnElTelefono || (servidor != null && servidor.activo && servidor.venceMillis > ahora)

/** Cuánto ahorra el plan anual frente a pagar doce meses del mensual, en porcentaje entero. */
fun ahorroAnual(mensualMicros: Long, anualMicros: Long): Int {
    val doceMeses = mensualMicros * 12
    if (mensualMicros <= 0 || anualMicros >= doceMeses) return 0
    return ((doceMeses - anualMicros) * 100 / doceMeses).toInt()
}

/**
 * Período ISO 8601 de Google Play a texto: "P14D" → "14 días", "P1W" → "1 semana", "P1Y" → "1 año".
 * Null si no lo reconoce, y entonces la pantalla no muestra la duración de la prueba.
 */
fun duracion(periodo: String): String? {
    val m = Regex("""P(\d+)([DWMY])""").matchEntire(periodo) ?: return null
    val n = m.groupValues[1].toInt()
    val (uno, varios) = when (m.groupValues[2]) {
        "D" -> "día" to "días"
        "W" -> "semana" to "semanas"
        "M" -> "mes" to "meses"
        else -> "año" to "años"
    }
    return "$n ${if (n == 1) uno else varios}"
}
