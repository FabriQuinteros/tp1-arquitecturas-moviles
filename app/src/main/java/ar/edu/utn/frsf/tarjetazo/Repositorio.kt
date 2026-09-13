package ar.edu.utn.frsf.tarjetazo

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal

// ponytail: SharedPreferences con JSON alcanza para los gastos de una tarjeta; pasar a Room si hay que filtrar o paginar.
class Repositorio(context: Context) {
    private val prefs = context.getSharedPreferences("tarjetazo", Context.MODE_PRIVATE)

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
    fun agregar(gasto: Gasto) = guardar(listOf(gasto) + gastos())

    fun eliminar(id: Long) = guardar(gastos().filterNot { it.id == id })

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
}
