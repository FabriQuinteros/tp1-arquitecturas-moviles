package ar.edu.utn.frsf.tarjetazo

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.android.billingclient.api.ProductDetails

/**
 * Pantalla de Tarjetazo Plus. Los precios salen siempre de Google Play, nunca del código: Play los
 * muestra en la moneda del usuario con los impuestos de su país, y cambiarlos no exige otra versión de la app.
 */
class PlusActivity : ActividadRegistrada() {
    private lateinit var mensual: Button
    private lateinit var anual: Button
    private lateinit var estado: TextView
    private lateinit var gestionar: Button
    private val actualizar = { mostrar() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_plus)
        mensual = findViewById(R.id.plan_mensual)
        anual = findViewById(R.id.plan_anual)
        estado = findViewById(R.id.estado)
        gestionar = findViewById(R.id.gestionar)

        // Desde qué función se llegó: sirve para contarle al usuario por qué está acá.
        intent.getStringExtra(MOTIVO)?.let { findViewById<TextView>(R.id.motivo).apply { text = it; visibility = View.VISIBLE } }

        mensual.setOnClickListener { comprar(PLAN_MENSUAL) }
        anual.setOnClickListener { comprar(PLAN_ANUAL) }
        // La cancelación la maneja Google Play: la app solo lleva a la pantalla de la suscripción.
        gestionar.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/account/subscriptions?sku=$PRODUCTO_PLUS&package=$packageName")))
        }
        findViewById<Button>(R.id.cerrar).setOnClickListener { finish() }
        Plus.iniciar(this)
    }

    override fun onStart() { super.onStart(); Plus.observar(actualizar); mostrar() }
    override fun onStop() { super.onStop(); Plus.dejarDeObservar(actualizar) }

    private fun comprar(planBase: String) {
        if (!Plus.comprar(this, planBase)) estado.text = "No se pudo abrir el pago de Google Play. Probá de nuevo en un rato."
    }

    private fun mostrar() {
        val activo = Plus.activo
        gestionar.visibility = if (activo) View.VISIBLE else View.GONE
        mensual.visibility = if (activo) View.GONE else View.VISIBLE
        anual.visibility = if (activo) View.GONE else View.VISIBLE
        estado.text = when {
            activo -> "Tenés Plus activo. ¡Gracias por bancar Tarjetazo!"
            Plus.pagoPendiente -> "Tu pago está pendiente. Plus se activa apenas Google Play lo acredite."
            Plus.producto == null -> "Plus no está disponible en este teléfono por ahora. Hace falta tener Google Play y la app instalada desde la tienda."
            else -> "Se renueva sola. Cancelás cuando quieras desde Google Play, sin perder tus gastos."
        }
        if (activo) return

        val mensualOferta = Plus.ofertaPara(PLAN_MENSUAL)
        val anualOferta = Plus.ofertaPara(PLAN_ANUAL)
        mensual.isEnabled = mensualOferta != null
        anual.isEnabled = anualOferta != null
        mensual.text = mensualOferta?.let { textoPlan("Mensual", "por mes", it) } ?: "Mensual"
        anual.text = anualOferta?.let { o ->
            val ahorro = ahorroAnual(precioNormal(mensualOferta)?.priceAmountMicros ?: 0, precioNormal(o)?.priceAmountMicros ?: 0)
            textoPlan("Anual", "por año", o) + if (ahorro > 0) " · ahorrás $ahorro %" else ""
        } ?: "Anual"
    }

    /** "Mensual · $ 1.999 por mes · 14 días gratis": la prueba gratis, si la hay, es la fase con precio cero. */
    private fun textoPlan(nombre: String, periodo: String, oferta: ProductDetails.SubscriptionOfferDetails): String {
        val prueba = oferta.pricingPhases.pricingPhaseList.firstOrNull { it.priceAmountMicros == 0L }?.let { duracion(it.billingPeriod) }
        return "$nombre · ${precioNormal(oferta)?.formattedPrice} $periodo" + (prueba?.let { " · $it gratis" } ?: "")
    }

    /** La última fase de la oferta es el precio que se paga una vez terminada la prueba. */
    private fun precioNormal(oferta: ProductDetails.SubscriptionOfferDetails?) =
        oferta?.pricingPhases?.pricingPhaseList?.lastOrNull()

    companion object {
        const val MOTIVO = "motivo"
    }
}
