package ar.edu.utn.frsf.tarjetazo

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import com.google.ads.mediation.admob.AdMobAdapter
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds

/**
 * Un único banner, abajo de la lista de gastos, y solo en el plan gratuito. Nunca en la carga de un
 * gasto ni en las deudas: ahí el usuario está haciendo lo que vino a hacer.
 *
 * Los anuncios son no personalizados: no usan el historial del usuario ni el ID de publicidad
 * (el permiso AD_ID se saca en el manifiesto). Pagan menos, pero una app que ve los gastos de la
 * tarjeta no puede alimentar perfiles publicitarios.
 */
object Anuncios {
    private var iniciado = false

    fun iniciar(context: Context) {
        if (iniciado) return
        iniciado = true
        // Google recomienda inicializar fuera del hilo principal: tarda y bloquearía la primera pantalla.
        Thread { MobileAds.initialize(context.applicationContext) }.start()
    }

    /** Muestra el banner en [contenedor] o lo saca si el usuario tiene Plus. Devuelve el banner para pausarlo y destruirlo. */
    fun mostrar(activity: Activity, contenedor: FrameLayout, actual: AdView?): AdView? {
        if (Plus.activo) {
            actual?.destroy()
            contenedor.removeAllViews()
            contenedor.visibility = View.GONE
            return null
        }
        if (actual != null) return actual
        iniciar(activity)
        // Ancho disponible en dp: la pantalla menos los 16 dp de margen de cada lado de activity_main.
        val ancho = (activity.resources.displayMetrics.run { widthPixels / density } - 32).toInt()
        val banner = AdView(activity).apply {
            adUnitId = BuildConfig.ADMOB_BANNER
            // Banner adaptable: Google elige el alto óptimo para el ancho de este teléfono.
            setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, ancho))
        }
        contenedor.removeAllViews()
        contenedor.addView(banner)
        contenedor.visibility = View.VISIBLE
        banner.loadAd(
            AdRequest.Builder()
                .addNetworkExtrasBundle(AdMobAdapter::class.java, Bundle().apply { putString("npa", "1") }) // no personalizado
                .build(),
        )
        return banner
    }
}
