package ar.edu.utn.frsf.tarjetazo

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Firebase Cloud Messaging, por dos vías:
 * - Avisos individuales (TP 6): el backend le escribe a cada teléfono por su token, con un texto
 *   armado para esa persona (su día de cierre, su resumen del mes, su racha). Por eso cada
 *   teléfono registra su token en `usuarios/{uid}/dispositivos/{token}`.
 * - Campañas (TP 6): se publican desde la consola de Firebase en el tema [TEMA_NOVEDADES], que
 *   llega a todos los suscriptos. El usuario puede salir del tema desde las preferencias.
 *
 * Con la app cerrada, el sistema muestra el aviso solo y, al tocarlo, abre la app con los datos
 * del mensaje como extras. Con la app abierta, el aviso llega a [onMessageReceived] y hay que mostrarlo a mano.
 */
class Avisos : FirebaseMessagingService() {
    override fun onMessageReceived(mensaje: RemoteMessage) {
        val aviso = mensaje.notification ?: return
        mostrar(this, aviso.title.orEmpty(), aviso.body.orEmpty(), mensaje.data["tipo"] ?: TIPO_NOVEDADES, mensaje.data[DESTINO])
    }

    // Google renueva el token cada tanto (reinstalación, datos borrados): hay que volver a registrarlo.
    override fun onNewToken(token: String) = registrar(token)

    companion object {
        const val TEMA_NOVEDADES = "novedades"
        private const val TEMA_VIEJO = "recordatorio-cierre" // el aviso igual para todos del TP 7

        // Un canal por tipo: el usuario puede silenciar uno desde los ajustes de Android sin perder los otros.
        const val TIPO_CIERRE = "cierre"
        const val TIPO_RESUMEN = "resumen"
        const val TIPO_INACTIVIDAD = "inactividad"
        const val TIPO_NOVEDADES = "novedades"
        private val CANALES = mapOf(
            TIPO_CIERRE to "Cierre de la tarjeta",
            TIPO_RESUMEN to "Resumen del mes",
            TIPO_INACTIVIDAD to "Recordatorio de carga",
            TIPO_NOVEDADES to "Novedades de Tarjetazo",
        )

        /** Al abrir la app con sesión: registra este teléfono y lo pasa del tema viejo al de campañas. */
        fun iniciar(context: Context, novedades: Boolean = true) {
            crearCanales(context)
            FirebaseMessaging.getInstance().token.addOnSuccessListener(::registrar)
            FirebaseMessaging.getInstance().unsubscribeFromTopic(TEMA_VIEJO)
            suscribirNovedades(novedades)
        }

        fun suscribirNovedades(si: Boolean) {
            val fcm = FirebaseMessaging.getInstance()
            if (si) fcm.subscribeToTopic(TEMA_NOVEDADES) else fcm.unsubscribeFromTopic(TEMA_NOVEDADES)
        }

        /** El token como id del documento: si llega dos veces, pisa el mismo. El backend borra los que Google da de baja. */
        private fun registrar(token: String) {
            // Solo en desarrollo: con este token se manda un mensaje de prueba desde la consola de Firebase.
            if (BuildConfig.DEBUG) android.util.Log.i("Avisos", "token FCM: $token")
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
            FirebaseFirestore.getInstance().document("usuarios/$uid/dispositivos/$token")
                .set(mapOf("plataforma" to "android", "actualizado" to FieldValue.serverTimestamp()))
        }

        /** Al cerrar sesión: este teléfono deja de recibir los avisos de esa cuenta. */
        fun olvidar() {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                FirebaseFirestore.getInstance().document("usuarios/$uid/dispositivos/$token").delete()
            }
        }

        private fun crearCanales(context: Context) {
            val gestor = context.getSystemService(NotificationManager::class.java)
            CANALES.forEach { (id, nombre) -> gestor.createNotificationChannel(NotificationChannel(id, nombre, NotificationManager.IMPORTANCE_DEFAULT)) }
        }

        fun mostrar(context: Context, titulo: String, texto: String, tipo: String, destino: String?) {
            crearCanales(context)
            // Tocar el aviso entra por el ingreso, que pasa el destino a la lista de gastos.
            val abrir = Intent(context, LoginActivity::class.java)
                .putExtra(DESTINO, destino)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            val pendiente = PendingIntent.getActivity(context, tipo.hashCode(), abrir, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            context.getSystemService(NotificationManager::class.java).notify(
                tipo.hashCode(), // uno por tipo: un aviso nuevo del mismo tipo reemplaza al anterior en lugar de apilarse
                NotificationCompat.Builder(context, if (tipo in CANALES) tipo else TIPO_NOVEDADES)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle(titulo)
                    .setContentText(texto)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
                    .setContentIntent(pendiente)
                    .setAutoCancel(true)
                    .build(),
            )
        }
    }
}
