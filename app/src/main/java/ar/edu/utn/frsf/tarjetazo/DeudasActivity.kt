package ar.edu.utn.frsf.tarjetazo

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView

class DeudasActivity : ActividadRegistrada() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_deudas)
        val texto = resumen(deudas(Repositorio(this).gastos()))
        findViewById<TextView>(R.id.resumen).text = texto

        // Intent implícito: se declara la acción y el sistema ofrece las apps que la atienden (RF-7).
        findViewById<Button>(R.id.compartir).setOnClickListener {
            val enviar = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, texto)
            startActivity(Intent.createChooser(enviar, "Compartir resumen"))
        }
    }
}
