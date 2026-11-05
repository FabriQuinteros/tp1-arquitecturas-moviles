"""Backend de Tarjetazo: recibe consumos y los procesa en segundo plano.

El mismo código se despliega como dos servicios de Cloud Run que escalan por separado:
- api:    POST /consumos publica en Pub/Sub y responde 202 sin esperar el procesamiento.
- worker: POST /pubsub/consumos es donde Pub/Sub entrega cada mensaje (suscripción push).
En local corre contra los emuladores: PUBSUB_EMULATOR_HOST y FIRESTORE_EMULATOR_HOST.
"""
import base64
import os
import time
from datetime import date
from decimal import Decimal
from typing import Literal

from fastapi import FastAPI
from google.cloud import firestore, pubsub_v1
from pydantic import BaseModel, Field

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
