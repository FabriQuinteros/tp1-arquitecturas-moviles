"""Backend de Tarjetazo: recibe consumos y los procesa en segundo plano.

El mismo código se despliega como dos servicios de Cloud Run que escalan por separado:
- api:    POST /consumos publica en Pub/Sub y responde 202 sin esperar el procesamiento.
- worker: POST /pubsub/consumos es donde Pub/Sub entrega cada mensaje (suscripción push).
          POST /pubsub/play recibe los avisos de Google Play sobre las suscripciones a Plus (TP 4).
En local corre contra los emuladores: PUBSUB_EMULATOR_HOST y FIRESTORE_EMULATOR_HOST.
"""
import base64
import json
import os
import time
from datetime import date, datetime, timezone
from decimal import Decimal
from typing import Literal

import google.auth
from fastapi import FastAPI
from google.auth.transport.requests import AuthorizedSession
from google.cloud import firestore, pubsub_v1
from pydantic import BaseModel, Field

import pagos

PROYECTO = os.environ.get("GOOGLE_CLOUD_PROJECT", "tarjetazo-local")
# Solo para la prueba de carga: simula que procesar depende de algo lento (un servicio externo,
# un resumen en PDF). En producción no se define y vale 0.
DEMORA_DEPENDENCIA = float(os.environ.get("DEMORA_DEPENDENCIA_S", "0"))

app = FastAPI(title="Tarjetazo")
db = firestore.Client(project=PROYECTO)
publicador = pubsub_v1.PublisherClient()
TEMA = publicador.topic_path(PROYECTO, os.environ.get("TEMA_CONSUMOS", "consumos"))


class Consumo(BaseModel):
    uid: str = Field(min_length=1, max_length=128)
    comercio: str = Field(min_length=1, max_length=100)
    importe: Decimal = Field(gt=0, max_digits=12, decimal_places=2)
    moneda: Literal["ARS", "USD"] = "ARS"
    fecha: date


def nuevo_id() -> str:
    # Mismo formato que la app (milisegundos), que ordena por id y lo lee como Long.
    # ponytail: choca si un mismo usuario manda dos consumos en el mismo milisegundo; pasar a ULID si pasa.
    return str(time.time_ns() // 1_000_000)


@app.post("/consumos", status_code=202)
def recibir(consumo: Consumo):
    id_ = nuevo_id()
    # Se espera la confirmación del broker (milisegundos), no el procesamiento: 202 significa
    # "quedó guardado en la cola", así que un consumo aceptado no se pierde aunque el worker esté caído.
    publicador.publish(TEMA, consumo.model_dump_json().encode(), id=id_).result(timeout=10)
    return {"id": id_}


@app.post("/consumos/sincronico", status_code=201)
def recibir_sincronico(consumo: Consumo):
    """Solo para la prueba de carga: el mismo trabajo, pero el cliente espera a que termine."""
    id_ = nuevo_id()
    procesar(consumo, id_)
    return {"id": id_}


@app.post("/pubsub/consumos", status_code=204)
def entregar(sobre: dict):
    # Pub/Sub reintenta todo lo que no responda 2xx y, después de 5 intentos, lo manda a la cola de errores.
    mensaje = sobre["message"]
    procesar(Consumo.model_validate_json(base64.b64decode(mensaje["data"])), mensaje["attributes"]["id"])


def procesar(consumo: Consumo, id_: str):
    time.sleep(DEMORA_DEPENDENCIA)
    importe = consumo.importe
    if consumo.moneda == "USD":
        # La cotización la deja la tarea programada; acá nunca se llama a DolarApi.
        cotizacion = db.document("cotizaciones/tarjeta").get()
        if not cotizacion.exists:
            raise RuntimeError("todavía no hay cotización cacheada")
        importe = (importe * Decimal(cotizacion.get("venta"))).quantize(Decimal("0.01"))

    # Pub/Sub entrega "al menos una vez": el mismo mensaje puede llegar dos veces. Escribir con el id
    # del mensaje como clave hace que repetirlo pise el mismo documento en lugar de duplicar el gasto.
    db.document(f"usuarios/{consumo.uid}/gastos/{id_}").set({
        "fecha": consumo.fecha.isoformat(),
        "comercio": consumo.comercio if consumo.moneda == "ARS" else f"{consumo.comercio} (US$ {consumo.importe})",
        "importe": str(importe),  # texto, como en la app: nada de double para la plata
        "division": [],
        "borrado": False,
        "actualizado": firestore.SERVER_TIMESTAMP,
    })


# ---------- Monetización: avisos de Google Play (TP 4) ----------

PLAY = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications"
_play: AuthorizedSession | None = None


def api_play() -> AuthorizedSession:
    # La cuenta de servicio del backend tiene que estar invitada en Play Console con permiso para ver
    # y administrar pedidos y suscripciones; si no, la API responde 401/403 y Pub/Sub reintenta.
    global _play
    if _play is None:
        credenciales, _ = google.auth.default(scopes=["https://www.googleapis.com/auth/androidpublisher"])
        _play = AuthorizedSession(credenciales)
    return _play


@app.post("/pubsub/play", status_code=204)
def aviso_play(sobre: dict):
    aviso = json.loads(base64.b64decode(sobre["message"]["data"]))
    cambio = aviso.get("subscriptionNotification")
    if not cambio:
        return  # el mensaje de prueba de Play Console, o avisos de otro tipo de producto
    paquete, token = aviso["packageName"], cambio["purchaseToken"]

    # El aviso trae solo el tipo de evento y el token: el estado se le pregunta a Google Play. Así no
    # importa si los avisos llegan desordenados o repetidos, y nadie puede inventar una compra
    # publicando un mensaje falso.
    respuesta = api_play().get(f"{PLAY}/{paquete}/purchases/subscriptionsv2/tokens/{token}", timeout=10)
    respuesta.raise_for_status()  # cualquier error → 5xx → Pub/Sub reintenta
    sub = respuesta.json()

    if pagos.necesita_confirmacion(sub):
        api_play().post(
            f"{PLAY}/{paquete}/purchases/subscriptions/{pagos.PRODUCTO}/tokens/{token}:acknowledge", json={}, timeout=10,
        ).raise_for_status()

    plan = pagos.plan_desde_suscripcion(sub, token, datetime.now(timezone.utc))
    if plan is None:
        return
    documento = db.document(f"usuarios/{plan.pop('uid')}")

    @firestore.transactional
    def guardar(transaccion):
        actual = documento.get(transaction=transaccion).to_dict() or {}
        if pagos.debe_reemplazar(actual.get("plus"), plan):
            transaccion.set(documento, {"plus": plan | {"actualizado": firestore.SERVER_TIMESTAMP}}, merge=True)

    guardar(db.transaction())
