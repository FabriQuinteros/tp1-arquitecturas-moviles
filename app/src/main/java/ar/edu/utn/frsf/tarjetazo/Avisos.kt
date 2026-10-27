package ar.edu.utn.frsf.tarjetazo

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Firebase Cloud Messaging con temas: publicador/suscriptor. El teléfono no sabe quién publica;
 * se suscribe a un tema y Google le entrega lo que se publique ahí por la conexión permanente
 * que mantienen los Servicios de Google Play. Con la app cerrada, el sistema muestra el aviso solo;
 * esta clase cubre el caso de la app abierta, en que el aviso llega acá y no se muestra si no se lo pide.
 */
class Avisos : FirebaseMessagingService() {
    override fun onMessageReceived(mensaje: RemoteMessage) {
        val aviso = mensaje.notification ?: return
        mostrar(this, aviso.title.orEmpty(), aviso.body.orEmpty())
    }

    companion object {
        const val TEMA_CIERRE = "recordatorio-cierre"
        private const val CANAL = "avisos"

        fun suscribir() = FirebaseMessaging.getInstance().subscribeToTopic(TEMA_CIERRE)

        fun mostrar(context: Context, titulo: String, texto: String) {
            val gestor = context.getSystemService(NotificationManager::class.java)
            gestor.createNotificationChannel(NotificationChannel(CANAL, "Avisos de la tarjeta", NotificationManager.IMPORTANCE_DEFAULT))
            gestor.notify(
                System.currentTimeMillis().toInt(),
                NotificationCompat.Builder(context, CANAL)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle(titulo)
                    .setContentText(texto)
                    .setAutoCancel(true)
                    .build(),
            )
        }
    }
}
