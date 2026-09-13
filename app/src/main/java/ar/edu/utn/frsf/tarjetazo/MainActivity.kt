package ar.edu.utn.frsf.tarjetazo

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class MainActivity : ActividadRegistrada() {
    private lateinit var repo: Repositorio
    private lateinit var lista: ListView
    private lateinit var total: TextView

    // Intent explícito con resultado: el editor devuelve el gasto y esta Activity lo guarda.
    // Mientras el editor está abierto, esta Activity queda detenida (onStop) debajo en la pila, no destruida.
    private val nuevoGasto = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val datos = r.data
        if (r.resultCode != RESULT_OK || datos == null) return@registerForActivityResult
        repo.agregar(
            Gasto(
                id = System.currentTimeMillis(),
                fecha = LocalDate.now().toString(),
                comercio = datos.getStringExtra(GastoEditorActivity.COMERCIO).orEmpty(),
                importe = BigDecimal(datos.getStringExtra(GastoEditorActivity.IMPORTE)),
                division = parseDivision(datos.getStringExtra(GastoEditorActivity.DIVISION).orEmpty()),
            ),
        )
        mostrar()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        repo = Repositorio(this)
        lista = findViewById(R.id.lista)
        total = findViewById(R.id.total)
        lista.emptyView = findViewById(R.id.vacio)

        findViewById<Button>(R.id.nuevo).setOnClickListener {
            nuevoGasto.launch(Intent(this, GastoEditorActivity::class.java))
        }
        findViewById<Button>(R.id.ver_deudas).setOnClickListener {
            startActivity(Intent(this, DeudasActivity::class.java))
        }
        lista.setOnItemLongClickListener { _, _, posicion, _ ->
            confirmarBorrado(repo.gastos()[posicion])
            true
        }
        mostrar()
    }

    private fun mostrar() {
        val gastos = repo.gastos()
        val formato = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        lista.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            gastos.map { g ->
                "${LocalDate.parse(g.fecha).format(formato)} · ${g.comercio} · ${moneda(g.importe)}\n" +
                    g.division.joinToString(" · ") { "${it.first} ${it.second} %" }
            },
        )
        total.text = "Total: ${moneda(gastos.sumOf { it.importe })}"
    }

    private fun confirmarBorrado(gasto: Gasto) {
        AlertDialog.Builder(this)
            .setMessage("¿Eliminar el gasto en ${gasto.comercio} por ${moneda(gasto.importe)}?")
            .setPositiveButton("Eliminar") { _, _ -> repo.eliminar(gasto.id); mostrar() }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
