package ar.edu.utn.frsf.tarjetazo

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

/**
 * Tarjetazo Plus: una suscripción de Google Play con dos planes base, mensual y anual.
 * Todo lo que toca Google Play Billing está en esta clase; las pantallas solo preguntan [activo]
 * y se enteran de los cambios con [observar].
 *
 * El estado sale de dos lugares:
 * - Google Play, en el teléfono: responde apenas se paga, aunque el backend no haya procesado nada.
 * - `usuarios/{uid}.plus` en Firestore: lo escribe el backend con lo que le avisa Google Play
 *   (renovaciones, cancelaciones, pagos rechazados) y vale para la cuenta en cualquier teléfono.
 */
object Plus {
    private var cliente: BillingClient? = null
    private var uid: String? = null
    private var escucha: ListenerRegistration? = null
    private var compraEnElTelefono = false
    private var servidor: PlusServidor? = null
    private val oyentes = mutableSetOf<() -> Unit>()

    /** Null mientras Google Play no respondió, o si en este teléfono no hay Google Play / el producto no existe. */
    var producto: ProductDetails? = null
        private set
    var pagoPendiente = false
        private set

    val activo get() = plusActivo(servidor, compraEnElTelefono, System.currentTimeMillis())

    fun iniciar(context: Context) {
        val cuenta = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (cuenta != uid) escucharServidor(cuenta)
        if (cliente == null) {
            cliente = BillingClient.newBuilder(context.applicationContext)
                .setListener { resultado, compras -> if (resultado.responseCode == BillingResponseCode.OK) procesar(compras.orEmpty()) }
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                .enableAutoServiceReconnection() // si Google Play se desconecta, reconecta solo en la próxima llamada
                .build()
            cliente!!.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(resultado: BillingResult) {
                    if (resultado.responseCode == BillingResponseCode.OK) { refrescar(); consultarProducto() }
                }
                override fun onBillingServiceDisconnected() {}
            })
        } else {
            refrescar()
        }
    }

    /** Vuelve a preguntarle a Google Play: la suscripción se puede cancelar desde la tienda, fuera de la app. */
    fun refrescar() {
        val c = cliente?.takeIf { it.isReady } ?: return
        c.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(ProductType.SUBS).build()) { resultado, compras ->
            if (resultado.responseCode == BillingResponseCode.OK) {
                compraEnElTelefono = false
                pagoPendiente = false
                procesar(compras)
            }
        }
    }

    /** Abre la hoja de pago de Google Play para un plan base, con la prueba gratis si le corresponde. */
    fun comprar(activity: Activity, planBase: String): Boolean {
        val c = cliente ?: return false
        val detalles = producto ?: return false
        val oferta = ofertaPara(planBase) ?: return false
        val parametros = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(detalles).setOfferToken(oferta.offerToken).build(),
            ))
            // Así el backend sabe de qué cuenta es la compra cuando Google Play le avisa. El uid de Firebase
            // es un identificador al azar, sin datos personales, que es lo que pide Google para este campo.
            .setObfuscatedAccountId(uid.orEmpty())
            .build()
        return c.launchBillingFlow(activity, parametros).responseCode == BillingResponseCode.OK
    }

    /**
     * Google Play devuelve solo las ofertas a las que esta cuenta tiene derecho. Si entre ellas hay una
     * con prueba gratis (la que no tiene offerId es el precio normal del plan), se ofrece esa.
     */
    fun ofertaPara(planBase: String): ProductDetails.SubscriptionOfferDetails? {
        val ofertas = producto?.subscriptionOfferDetails.orEmpty().filter { it.basePlanId == planBase }
        return ofertas.firstOrNull { it.offerId != null } ?: ofertas.firstOrNull()
    }

    fun observar(oyente: () -> Unit) { oyentes += oyente }
    fun dejarDeObservar(oyente: () -> Unit) { oyentes -= oyente }

    /** Al cerrar sesión: el próximo que entre no tiene que heredar el Plus de esta cuenta. */
    fun cerrar() {
        escucha?.remove()
        escucha = null
        uid = null
        servidor = null
        compraEnElTelefono = false
        pagoPendiente = false
        avisar()
    }

    private fun procesar(compras: List<Purchase>) {
        compras.filter { PRODUCTO_PLUS in it.products }.forEach { compra ->
            when (compra.purchaseState) {
                Purchase.PurchaseState.PURCHASED -> {
                    compraEnElTelefono = true
                    // Google Play devuelve el dinero si la compra no se confirma en 3 días. El backend también
                    // la confirma al recibir el aviso; confirmarla dos veces no tiene efecto.
                    if (!compra.isAcknowledged) {
                        cliente?.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(compra.purchaseToken).build()) {}
                    }
                }
                Purchase.PurchaseState.PENDING -> pagoPendiente = true // p. ej. pago en efectivo que todavía no se acreditó
            }
        }
        avisar()
    }

    private fun consultarProducto() {
        val pedido = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(QueryProductDetailsParams.Product.newBuilder().setProductId(PRODUCTO_PLUS).setProductType(ProductType.SUBS).build()))
            .build()
        cliente?.queryProductDetailsAsync(pedido) { resultado, detalles ->
            if (resultado.responseCode == BillingResponseCode.OK) producto = detalles.productDetailsList.firstOrNull()
            avisar()
        }
    }

    private fun escucharServidor(cuenta: String) {
        escucha?.remove()
        uid = cuenta
        servidor = null
        escucha = FirebaseFirestore.getInstance().document("usuarios/$cuenta").addSnapshotListener { doc, _ ->
            val plus = doc?.get("plus") as? Map<*, *>
            servidor = plus?.let {
                PlusServidor(activo = it["activo"] == true, venceMillis = (it["vence"] as? Timestamp)?.toDate()?.time ?: 0)
            }
            avisar()
        }
    }

    // Los listeners de Billing pueden llegar en otro hilo; las pantallas se actualizan en el principal.
    private fun avisar() = Handler(Looper.getMainLooper()).post { oyentes.toList().forEach { it() } }
}
