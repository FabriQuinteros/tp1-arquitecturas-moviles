package ar.edu.utn.frsf.tarjetazo

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import com.google.android.gms.ads.AdView
import androidx.core.content.edit
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

class MainActivity : ActividadRegistrada() {
    private lateinit var repo: Repositorio
    private lateinit var lista: ListView
    private lateinit var total: TextView
    private lateinit var progreso: TextView
    private lateinit var botonPlus: Button
    private var banner: AdView? = null
    private val planCambio = { mostrarPlan() }

    // Intent explícito con resultado: el editor devuelve el gasto y esta Activity lo guarda.
    // Mientras el editor está abierto, esta Activity queda detenida (onStop) debajo en la pila, no destruida.
    private val nuevoGasto = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val datos = r.data
        if (r.resultCode != RESULT_OK || datos == null) return@registerForActivityResult
        val gasto = Gasto(
            id = System.currentTimeMillis(),
            fecha = LocalDate.now().toString(),
            comercio = datos.getStringExtra(GastoEditorActivity.COMERCIO).orEmpty(),
            importe = BigDecimal(datos.getStringExtra(GastoEditorActivity.IMPORTE)),
            division = parseDivision(datos.getStringExtra(GastoEditorActivity.DIVISION).orEmpty()),
        )
        repo.agregar(gasto)
        mostrar()
        pedirPermisoAvisos()
    }

    // Desde Android 13 mostrar notificaciones requiere permiso explícito; sin él, los avisos no se ven.
    private val permisoAvisos = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sesion = FirebaseAuth.getInstance()
        if (sesion.currentUser == null) {
            // La sesión se cerró desde otro lado, o el proceso volvió sin ella.
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)
        repo = Repositorio(this)
        findViewById<TextView>(R.id.cuenta).text = sesion.currentUser?.email
        findViewById<Button>(R.id.salir).setOnClickListener {
            Avisos.olvidar()
            Plus.cerrar()
            sesion.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
        findViewById<Button>(R.id.avisos).setOnClickListener { startActivity(Intent(this, AvisosActivity::class.java)) }
        botonPlus = findViewById(R.id.plus)
        botonPlus.setOnClickListener { startActivity(Intent(this, PlusActivity::class.java)) }
        Plus.iniciar(this)
        lista = findViewById(R.id.lista)
        total = findViewById(R.id.total)
        progreso = findViewById(R.id.progreso)
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
        repo.sincronizar { if (!isDestroyed) mostrar() }

        Avisos.iniciar(this)
        // Solo la primera vez: si se recrea por una rotación, ya se abrió la pantalla del aviso.
        if (savedInstanceState == null) abrirDestino(intent.getStringExtra(DESTINO))
    }

    /** Un aviso tocado lleva directo a lo que propone: cargar un gasto o revisar las deudas. */
    private fun abrirDestino(destino: String?) = when (destino) {
        DESTINO_NUEVO_GASTO -> nuevoGasto.launch(Intent(this, GastoEditorActivity::class.java))
        DESTINO_DEUDAS -> startActivity(Intent(this, DeudasActivity::class.java))
        else -> Unit
    }

    /**
     * El permiso se pide después del primer gasto y con una explicación, no al abrir la app por primera
     * vez: recién ahí el usuario sabe para qué le sirve un aviso. Si lo rechaza, no se insiste.
     */
    private fun pedirPermisoAvisos() {
        if (Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        val prefs = getSharedPreferences("tarjetazo", MODE_PRIVATE)
        if (prefs.getBoolean("permiso_avisos_pedido", false)) return
        prefs.edit { putBoolean("permiso_avisos_pedido", true) }
        AlertDialog.Builder(this)
            .setTitle("¿Te avisamos antes del cierre?")
            .setMessage("Tarjetazo puede recordarte revisar y dividir los gastos antes de que cierre la tarjeta, y mandarte el resumen del mes. Como mucho un aviso por día, y los podés apagar cuando quieras.")
            .setPositiveButton("Sí, avisame") { _, _ -> permisoAvisos.launch(Manifest.permission.POST_NOTIFICATIONS) }
            .setNegativeButton("Ahora no", null)
            .show()
    }

    override fun onStart() {
        super.onStart()
        Plus.observar(planCambio)
        mostrarPlan()
    }

    override fun onResume() {
        super.onResume()
        // La tarea diaria del backend usa esta fecha para no mandar recordatorios a quien sí usa la app.
        FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
            FirebaseFirestore.getInstance().document("usuarios/$uid")
                .set(mapOf("ultimaApertura" to FieldValue.serverTimestamp()), SetOptions.merge())
        }
        banner?.resume()
        // La suscripción se puede cancelar o renovar desde Google Play, fuera de la app.
        Plus.refrescar()
    }

    override fun onPause() {
        banner?.pause()
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        Plus.dejarDeObservar(planCambio)
    }

    override fun onDestroy() {
        banner?.destroy()
        super.onDestroy()
    }

    /** Con Plus desaparece el banner y el botón lo dice; sin Plus, el botón lleva a la suscripción. */
    private fun mostrarPlan() {
        botonPlus.text = if (Plus.activo) "Plus ✓" else "Plus"
        banner = Anuncios.mostrar(this, findViewById<FrameLayout>(R.id.anuncio), banner)
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
        // Contenido personalizado: la racha y el resumen del mes en curso, calculados con los gastos de esta cuenta.
        val hoy = LocalDate.now()
        progreso.text = listOfNotNull(
            textoRacha(racha(gastos.map { LocalDate.parse(it.fecha) }, hoy)),
            textoResumen(resumenDelMes(gastos, YearMonth.from(hoy)), YearMonth.from(hoy)),
        ).joinToString("\n")
        progreso.visibility = if (progreso.text.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun confirmarBorrado(gasto: Gasto) {
        AlertDialog.Builder(this)
            .setMessage("¿Eliminar el gasto en ${gasto.comercio} por ${moneda(gasto.importe)}?")
            .setPositiveButton("Eliminar") { _, _ -> repo.eliminar(gasto.id); mostrar() }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
