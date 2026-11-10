package ar.edu.utn.frsf.tarjetazo

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

/**
 * Qué avisos quiere recibir el usuario. Se guardan en `usuarios/{uid}.avisos`, que es lo que lee
 * la tarea diaria del backend antes de mandar nada. Todos se pueden apagar: un aviso que molesta
 * y no se puede silenciar termina en una desinstalación, que es lo contrario de retener.
 */
class AvisosActivity : ActividadRegistrada() {
    private val documento by lazy {
        FirebaseFirestore.getInstance().document("usuarios/${FirebaseAuth.getInstance().currentUser?.uid}")
    }
    private lateinit var cierre: SwitchCompat
    private lateinit var diaCierre: EditText
    private lateinit var resumen: SwitchCompat
    private lateinit var inactividad: SwitchCompat
    private lateinit var novedades: SwitchCompat
    private lateinit var estado: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_avisos)
        cierre = findViewById(R.id.aviso_cierre)
        diaCierre = findViewById(R.id.dia_cierre)
        resumen = findViewById(R.id.aviso_resumen)
        inactividad = findViewById(R.id.aviso_inactividad)
        novedades = findViewById(R.id.aviso_novedades)
        estado = findViewById(R.id.estado)
        cierre.setOnCheckedChangeListener { _, si -> diaCierre.isEnabled = si }

        // Con estado guardado (rotación) el sistema ya restauró los controles; si no, se leen de Firestore.
        if (savedInstanceState == null) cargar()
        findViewById<Button>(R.id.guardar).setOnClickListener { guardar() }
    }

    private fun cargar() {
        documento.get().addOnSuccessListener { doc ->
            val p = PreferenciasAvisos.desde(doc.get("avisos") as? Map<*, *>)
            cierre.isChecked = p.cierre
            diaCierre.setText(p.diaCierre?.toString().orEmpty())
            diaCierre.isEnabled = p.cierre
            resumen.isChecked = p.resumen
            inactividad.isChecked = p.inactividad
            novedades.isChecked = p.novedades
        }
    }

    private fun guardar() {
        val dia = diaCierre.text.toString().toIntOrNull()
        if (cierre.isChecked && (dia == null || dia !in 1..31)) {
            estado.text = "Escribí el día de cierre de tu tarjeta (de 1 a 31), o apagá ese aviso."
            return
        }
        val p = PreferenciasAvisos(cierre.isChecked, dia, resumen.isChecked, inactividad.isChecked, novedades.isChecked)
        documento.set(mapOf("avisos" to p.aMapa()), SetOptions.merge())
        Avisos.suscribirNovedades(p.novedades)
        // Firestore guarda primero en el teléfono y sube al volver la señal: no hace falta esperar la respuesta.
        estado.text = "Listo. Vas a recibir solo los avisos que dejaste prendidos."
    }
}

/** Por defecto todo prendido menos el cierre, que necesita que el usuario diga su día. */
data class PreferenciasAvisos(
    val cierre: Boolean = false,
    val diaCierre: Int? = null,
    val resumen: Boolean = true,
    val inactividad: Boolean = true,
    val novedades: Boolean = true,
) {
    fun aMapa() = mapOf("cierre" to cierre, "diaCierre" to diaCierre, "resumen" to resumen, "inactividad" to inactividad, "novedades" to novedades)

    companion object {
        fun desde(m: Map<*, *>?): PreferenciasAvisos {
            if (m == null) return PreferenciasAvisos()
            return PreferenciasAvisos(
                cierre = m["cierre"] == true,
                diaCierre = (m["diaCierre"] as? Number)?.toInt(),
                resumen = m["resumen"] != false,
                inactividad = m["inactividad"] != false,
                novedades = m["novedades"] != false,
            )
        }
    }
}
