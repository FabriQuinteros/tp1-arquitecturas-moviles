package ar.edu.utn.frsf.tarjetazo

import android.content.Context
import androidx.core.content.edit
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal

/**
 * Dos niveles: SharedPreferences es la copia local que lee la interfaz, y Cloud Firestore
 * guarda los gastos de la cuenta en `usuarios/{uid}/gastos/{id}`. Cada escritura va a los dos;
 * Firestore la encola si no hay conexión y la sube al volver la señal.
 * La copia local va por cuenta, así dos personas en el mismo teléfono no ven los gastos de la otra.
 */
class Repositorio(context: Context) {
    private val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    private val prefs = context.getSharedPreferences("tarjetazo_$uid", Context.MODE_PRIVATE)
    private val remotos = FirebaseFirestore.getInstance().collection("usuarios").document(uid).collection("gastos")

    fun gastos(): List<Gasto> {
        val arr = JSONArray(prefs.getString("gastos", "[]"))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val div = o.getJSONArray("division")
            Gasto(
                id = o.getLong("id"),
                fecha = o.getString("fecha"),
                comercio = o.getString("comercio"),
                importe = BigDecimal(o.getString("importe")),
                division = (0 until div.length()).map { j -> div.getJSONArray(j).let { it.getString(0) to it.getInt(1) } },
            )
        }
    }

    /** Más reciente primero (RF-4). */
    fun agregar(gasto: Gasto) {
        guardar(listOf(gasto) + gastos())
        remotos.document(gasto.id.toString()).set(aDocumento(gasto))
    }

    fun eliminar(id: Long) {
        guardar(gastos().filterNot { it.id == id })
        remotos.document(id.toString()).delete()
    }

    /**
     * Trae los gastos de la cuenta y reemplaza la copia local: así aparecen en un teléfono nuevo.
     * Si la nube está vacía y el teléfono no, es la primera vez de esta cuenta: se suben los locales.
     */
    fun sincronizar(listo: () -> Unit) {
        remotos.get().addOnSuccessListener { resultado ->
            if (resultado.isEmpty) {
                gastos().forEach { remotos.document(it.id.toString()).set(aDocumento(it)) }
            } else {
                guardar(resultado.documents.mapNotNull { desdeDocumento(it.id, it.data) }.sortedByDescending { it.id })
            }
            listo()
        }
    }

    fun leerBorrador(campo: String): String = prefs.getString("borrador_$campo", "").orEmpty()

    fun guardarBorrador(campos: Map<String, String>) = prefs.edit { campos.forEach { (k, v) -> putString("borrador_$k", v) } }

    private fun guardar(gastos: List<Gasto>) {
        val arr = JSONArray()
        gastos.forEach { g ->
            arr.put(
                JSONObject()
                    .put("id", g.id)
                    .put("fecha", g.fecha)
                    .put("comercio", g.comercio)
                    .put("importe", g.importe.toPlainString()) // texto, no double: RNF-3
                    .put("division", JSONArray(g.division.map { JSONArray().put(it.first).put(it.second) })),
            )
        }
        prefs.edit { putString("gastos", arr.toString()) }
    }

    // El importe viaja como texto también en Firestore: un número ahí sería double (RNF-3).
    private fun aDocumento(g: Gasto) = mapOf(
        "fecha" to g.fecha,
        "comercio" to g.comercio,
        "importe" to g.importe.toPlainString(),
        "division" to g.division.map { mapOf("nombre" to it.first, "porcentaje" to it.second) },
    )

    private fun desdeDocumento(id: String, d: Map<String, Any>?): Gasto? = runCatching {
        Gasto(
            id = id.toLong(),
            fecha = d!!["fecha"] as String,
            comercio = d["comercio"] as String,
            importe = BigDecimal(d["importe"] as String),
            division = (d["division"] as List<*>).map { p -> (p as Map<*, *>).let { it["nombre"] as String to (it["porcentaje"] as Number).toInt() } },
        )
    }.getOrNull()
}
