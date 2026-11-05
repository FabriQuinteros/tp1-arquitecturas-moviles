"""Tareas programadas. En la nube las dispara Cloud Scheduler como Cloud Run Jobs; en local, a mano:

    python tareas.py cotizacion     # cada 30 minutos
    python tareas.py recordatorio   # los días previos al cierre
"""
import json
import os
import sys
import urllib.request

from google.cloud import firestore

PROYECTO = os.environ.get("GOOGLE_CLOUD_PROJECT", "tarjetazo-local")


def cotizacion():
    # Una sola llamada a DolarApi para todos los usuarios; la app y el worker leen el valor de Firestore.
    # DolarApi rechaza (403) el User-Agent por defecto de Python: hay que identificarse.
    pedido = urllib.request.Request("https://dolarapi.com/v1/dolares/tarjeta", headers={"User-Agent": "tarjetazo-backend/1.0"})
    with urllib.request.urlopen(pedido, timeout=10) as r:
        dolar = json.load(r)
    firestore.Client(project=PROYECTO).document("cotizaciones/tarjeta").set({
        "venta": str(dolar["venta"]),
        "compra": str(dolar["compra"]),
        "fuente": dolar["fechaActualizacion"],
        "actualizado": firestore.SERVER_TIMESTAMP,
    })
    print("cotizacion guardada:", dolar["venta"])


def recordatorio():
    mensaje = {"titulo": "Se acerca el cierre de la tarjeta", "texto": "Revisá y dividí los gastos pendientes."}
    if os.environ.get("FIRESTORE_EMULATOR_HOST"):
        print("modo local, Cloud Messaging no tiene emulador; se publicaria:", mensaje)
        return
    import firebase_admin
    from firebase_admin import messaging

    firebase_admin.initialize_app()
    # Una publicación en el tema: Google la reparte a todos los teléfonos suscriptos, sean 10 o 10 millones.
    messaging.send(messaging.Message(
        topic="recordatorio-cierre",
        notification=messaging.Notification(title=mensaje["titulo"], body=mensaje["texto"]),
    ))
    print("recordatorio publicado")


if __name__ == "__main__":
    {"cotizacion": cotizacion, "recordatorio": recordatorio}[sys.argv[1]]()
