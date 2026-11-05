"""Solo local: crea en el emulador el tema y la suscripción push que en la nube crea infra/main.tf."""
import os

from google.api_core.exceptions import AlreadyExists
from google.cloud import pubsub_v1

PROYECTO = os.environ.get("GOOGLE_CLOUD_PROJECT", "tarjetazo-local")
publicador = pubsub_v1.PublisherClient()
suscriptor = pubsub_v1.SubscriberClient()
tema = publicador.topic_path(PROYECTO, "consumos")
try:
    publicador.create_topic(name=tema)
    suscriptor.create_subscription(
        name=suscriptor.subscription_path(PROYECTO, "consumos-worker"),
        topic=tema,
        push_config={"push_endpoint": "http://balanceador/pubsub/consumos"},
        ack_deadline_seconds=30,
    )
except AlreadyExists:
    pass
print("tema y suscripcion listos")
