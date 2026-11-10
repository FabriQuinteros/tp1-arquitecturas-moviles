"""Tareas programadas. En la nube las dispara Cloud Scheduler como Cloud Run Jobs; en local, a mano:

    python tareas.py cotizacion     # cada 30 minutos
    python tareas.py avisos         # todos los días a las 10: un aviso personalizado por usuario, si corresponde (TP 6)
    python tareas.py campania "Título" "Texto" [todos|inactivos]   # a mano, cuando se lanza una campaña (TP 6)
"""
import json
import os
import sys
import urllib.request
from datetime import date, datetime, timedelta
from zoneinfo import ZoneInfo

from google.cloud import firestore

import retencion

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


# ---------- Retención: avisos personalizados y campañas (TP 6) ----------
# Reemplaza al recordatorio del TP 3, que era el mismo texto para todos, del 20 al 24 de cada mes.

LOCAL = bool(os.environ.get("FIRESTORE_EMULATOR_HOST"))  # Cloud Messaging no tiene emulador: en local solo se imprime
HOY = lambda: datetime.now(ZoneInfo("America/Argentina/Buenos_Aires")).date()


def _mensajero():
    import firebase_admin
    from firebase_admin import messaging
    if not firebase_admin._apps:
        firebase_admin.initialize_app()
    return messaging


def enviar(db, uid: str, tokens: list[str], aviso: dict) -> int:
    """Manda el aviso a todos los teléfonos de la cuenta. Devuelve cuántos lo recibieron y borra los tokens dados de baja."""
    if LOCAL:
        print(f"[local] {uid} <- {aviso['tipo']}: {aviso['titulo']} - {aviso['texto']}")
        return len(tokens)
    messaging = _mensajero()
    mensajes = [messaging.Message(
        token=t,
        notification=messaging.Notification(title=aviso["titulo"], body=aviso["texto"]),
        # Los datos llegan a la app como extras: el tipo elige el canal y el destino, la pantalla que se abre.
        data={"tipo": aviso["tipo"], "destino": aviso.get("destino") or ""},
        android=messaging.AndroidConfig(notification=messaging.AndroidNotification(channel_id=aviso["tipo"])),
    ) for t in tokens]
    respuesta = messaging.send_each(mensajes)
    for token, r in zip(tokens, respuesta.responses):
        # La app se desinstaló o se borraron sus datos: ese token no va a volver a funcionar.
        if not r.success and isinstance(r.exception, messaging.UnregisteredError):
            db.document(f"usuarios/{uid}/dispositivos/{token}").delete()
    return respuesta.success_count


def _cuentas_con_telefono(db):
    """(uid, datos de usuarios/{uid}, tokens). Solo las cuentas con algún teléfono registrado."""
    por_cuenta: dict[str, list[str]] = {}
    for d in db.collection_group("dispositivos").stream():
        por_cuenta.setdefault(d.reference.parent.parent.id, []).append(d.id)
    for uid, tokens in por_cuenta.items():
        yield uid, db.document(f"usuarios/{uid}").get().to_dict() or {}, tokens


def _fecha(valor) -> date | None:
    return valor.astimezone(ZoneInfo("America/Argentina/Buenos_Aires")).date() if valor else None


def avisos():
    db = firestore.Client(project=PROYECTO)
    hoy = HOY()
    desde = (hoy.replace(day=1) - timedelta(days=62)).isoformat()  # mes anterior completo y la racha de ~8 semanas
    enviados = 0
    for uid, usuario, tokens in _cuentas_con_telefono(db):
        # Se leen solo los gastos recientes: Firestore cobra por documento leído.
        gastos = [g.to_dict() for g in db.collection(f"usuarios/{uid}/gastos").where("fecha", ">=", desde).stream()]
        aviso = retencion.elegir_aviso(usuario.get("avisos"), _fecha(usuario.get("ultimaApertura")), usuario.get("estadoAvisos"), gastos, hoy)
        if aviso is None:
            continue
        enviados += 1 if enviar(db, uid, tokens, aviso) else 0
        anterior = usuario.get("estadoAvisos") or {}
        db.document(f"usuarios/{uid}").set({"estadoAvisos": {
            "ultimo": hoy.isoformat(),
            "tipo": aviso["tipo"],
            "recordatoriosSinRespuesta": aviso.get("recordatoriosSinRespuesta", anterior.get("recordatoriosSinRespuesta", 0)),
        }}, merge=True)
    print(f"avisos enviados: {enviados}")


def campania(titulo: str, texto: str, segmento: str = "todos"):
    """
    todos: una sola publicación en el tema de novedades; Google la reparte a todos los suscriptos.
    inactivos: reactivación, uno por uno a las cuentas que no abren la app hace dos semanas o más,
    respetando a quien apagó las novedades.
    """
    aviso = {"tipo": "novedades", "titulo": titulo, "texto": texto, "destino": "nuevo_gasto"}
    if segmento == "todos":
        if LOCAL:
            print("[local] tema novedades <-", aviso)
            return
        messaging = _mensajero()
        messaging.send(messaging.Message(
            topic="novedades",
            notification=messaging.Notification(title=titulo, body=texto),
            data={"tipo": "novedades", "destino": "nuevo_gasto"},
            android=messaging.AndroidConfig(notification=messaging.AndroidNotification(channel_id="novedades")),
        ))
        print("campaña publicada en el tema novedades")
        return
    db = firestore.Client(project=PROYECTO)
    hoy = HOY()
    alcanzados = 0
    for uid, usuario, tokens in _cuentas_con_telefono(db):
        if retencion.preferencias(usuario.get("avisos"))["novedades"] and retencion.inactivo_para_campania(_fecha(usuario.get("ultimaApertura")), hoy):
            alcanzados += 1 if enviar(db, uid, tokens, aviso) else 0
    print(f"campaña de reactivación: {alcanzados} cuentas")


if __name__ == "__main__":
    tarea = sys.argv[1]
    if tarea == "campania":
        campania(*sys.argv[2:5])
    else:
        {"cotizacion": cotizacion, "avisos": avisos}[tarea]()
