package ar.edu.utn.frsf.tarjetazo

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth

/**
 * Ingreso con correo y contraseña contra Firebase Authentication.
 * Es la primera pantalla: sin sesión abierta no se llega a los gastos.
 */
class LoginActivity : ActividadRegistrada() {
    private val sesion by lazy { FirebaseAuth.getInstance() }
    private lateinit var correo: EditText
    private lateinit var clave: EditText
    private lateinit var botones: List<Button>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (sesion.currentUser != null) return abrirGastos()

        setContentView(R.layout.activity_login)
        correo = findViewById(R.id.correo)
        clave = findViewById(R.id.clave)
        val entrar = findViewById<Button>(R.id.entrar)
        val crear = findViewById<Button>(R.id.crear_cuenta)
        botones = listOf(entrar, crear)

        entrar.setOnClickListener { conCredenciales { c, p -> sesion.signInWithEmailAndPassword(c, p) } }
        crear.setOnClickListener { conCredenciales { c, p -> sesion.createUserWithEmailAndPassword(c, p) } }
    }

    private fun conCredenciales(accion: (String, String) -> com.google.android.gms.tasks.Task<*>) {
        val c = correo.text.toString().trim()
        val p = clave.text.toString()
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(c).matches()) return avisar("Correo inválido")
        if (p.length < 6) return avisar("La contraseña necesita al menos 6 caracteres")

        habilitar(false)
        accion(c, p)
            .addOnSuccessListener { abrirGastos() }
            .addOnFailureListener {
                habilitar(true)
                findViewById<TextView>(R.id.error).text = it.localizedMessage ?: "No se pudo ingresar"
            }
    }

    private fun abrirGastos() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun habilitar(valor: Boolean) = botones.forEach { it.isEnabled = valor }

    private fun avisar(mensaje: String) = Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show()
}
