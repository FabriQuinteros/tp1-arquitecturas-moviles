package ar.edu.utn.frsf.tarjetazo

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

/** Escribe en Logcat cada evento del ciclo de vida. Para verlo: Logcat, filtro tag:Ciclo. */
open class ActividadRegistrada : AppCompatActivity() {
    private fun registrar(evento: String) = Log.d("Ciclo", "${javaClass.simpleName}.$evento")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registrar("onCreate (estado guardado: ${savedInstanceState != null})")
    }

    override fun onStart() { super.onStart(); registrar("onStart") }
    override fun onResume() { super.onResume(); registrar("onResume") }
    override fun onPause() { super.onPause(); registrar("onPause") }
    override fun onStop() { super.onStop(); registrar("onStop") }
    override fun onRestart() { super.onRestart(); registrar("onRestart") }
    override fun onDestroy() { super.onDestroy(); registrar("onDestroy") }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        registrar("onSaveInstanceState")
    }
}
