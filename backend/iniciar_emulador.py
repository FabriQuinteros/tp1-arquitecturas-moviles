"""Solo local: crea en el emulador los temas y las suscripciones push que en la nube crea infra/main.tf."""
import os

from google.api_core.exceptions import AlreadyExists
from google.cloud import pubsub_v1

PROYECTO = os.environ.get("GOOGLE_CLOUD_PROJECT", "tarjetazo-local")
publicador = pubsub_v1.PublisherClient()
suscriptor = pubsub_v1.SubscriberClient()
# consumos: lo que mandan los importadores (TP 3). play-rtdn: lo que en la nube publica Google Play (TP 4).
for tema_, ruta in [("consumos", "/pubsub/consumos"), ("play-rtdn", "/pubsub/play")]:
    tema = publicador.topic_path(PROYECTO, tema_)
    try:
        publicador.create_topic(name=tema)
        suscriptor.create_subscription(
            name=suscriptor.subscription_path(PROYECTO, f"{tema_}-worker"),
            topic=tema,
            push_config={"push_endpoint": f"http://balanceador{ruta}"},
            ack_deadline_seconds=30,
        )
    except AlreadyExists:
        pass
print("temas y suscripciones listos")
