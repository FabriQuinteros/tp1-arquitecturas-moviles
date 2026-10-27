package ar.edu.utn.frsf.tarjetazo

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import java.math.BigDecimal

/**
 * Alta de un gasto. Cómo responde al ciclo de vida:
 * - Rotación: el sistema destruye y recrea la Activity, y los EditText con id
 *   recuperan su texto solos a través de onSaveInstanceState.
 * - El usuario sale y el sistema mata el proceso: onPause ya dejó el borrador
 *   guardado, y onCreate lo vuelve a cargar la próxima vez (RF-8).
 */
class GastoEditorActivity : ActividadRegistrada() {
    private lateinit var repo: Repositorio
    private lateinit var campos: Map<String, EditText>
    private var terminado = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gasto_editor)
        repo = Repositorio(this)
        campos = mapOf(
            COMERCIO to findViewById(R.id.comercio),
            IMPORTE to findViewById(R.id.importe),
            DIVISION to findViewById(R.id.division),
        )
        // Con estado guardado el sistema ya restauró los campos; sin él, se parte del borrador persistido.
        if (savedInstanceState == null) campos.forEach { (campo, vista) -> vista.setText(repo.leerBorrador(campo)) }

        findViewById<Button>(R.id.guardar).setOnClickListener { guardar() }
        findViewById<Button>(R.id.cancelar).setOnClickListener { terminar(RESULT_CANCELED, null) }
    }

    override fun onPause() {
        super.onPause()
        // Último momento garantizado antes de que el sistema pueda terminar el proceso.
        // Si el gasto ya se guardó o se canceló, el borrador se vacía.
        repo.guardarBorrador(campos.mapValues { if (terminado) "" else it.value.text.toString() })
    }

    private fun guardar() {
        val comercio = texto(COMERCIO).ifEmpty { return avisar("Falta el comercio") }
        val importe = parseImporte(texto(IMPORTE)) ?: return avisar("Importe inválido. Ejemplo: 10.000,50")
        val division = texto(DIVISION)
        runCatching { parseDivision(division) }.onFailure { return avisar(it.message.orEmpty()) }

        if (!findViewById<CheckBox>(R.id.en_dolares).isChecked) return devolver(comercio, importe, division, false)

        // La consulta a la API es de red: va en otro hilo y vuelve al principal para tocar la interfaz.
        val boton = findViewById<Button>(R.id.guardar).apply { isEnabled = false }
        Thread {
            val cotizacion = runCatching { Cotizacion.dolarTarjeta() }
            runOnUiThread {
                boton.isEnabled = true
                cotizacion
                    .onSuccess { dolar -> devolver("$comercio (US$ ${importe.toPlainString()} a $dolar)", Cotizacion.aPesos(importe, dolar), division, true) }
                    .onFailure { avisar("No se pudo obtener la cotización del dólar. Revisá la conexión.") }
            }
        }.start()
    }

    private fun devolver(comercio: String, importe: BigDecimal, division: String, enDolares: Boolean) = terminar(
        RESULT_OK,
        Intent()
            .putExtra(COMERCIO, comercio)
            .putExtra(IMPORTE, importe.toPlainString())
            .putExtra(DIVISION, division)
            .putExtra(EN_DOLARES, enDolares),
    )

    private fun terminar(resultado: Int, datos: Intent?) {
        terminado = true
        setResult(resultado, datos)
        finish()
    }

    private fun texto(campo: String) = campos.getValue(campo).text.toString().trim()

    private fun avisar(mensaje: String) = Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show()

    companion object {
        // Sirven de clave para los extras del Intent de resultado y para el borrador.
        const val COMERCIO = "comercio"
        const val IMPORTE = "importe"
        const val DIVISION = "division"
        const val EN_DOLARES = "en_dolares"
    }
}
