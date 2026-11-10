"""Estado de Tarjetazo Plus a partir de las notificaciones de Google Play (RTDN).

Google Play publica un mensaje en Pub/Sub cada vez que una suscripción cambia: compra, renovación,
cancelación, pago rechazado, vencimiento. El mensaje trae solo el tipo de evento y el token de compra,
así que el worker le pregunta a la API de Google Play el estado completo y lo guarda en
`usuarios/{uid}.plus`, que es lo que lee la app.

Las funciones de este archivo no tocan la red ni Firestore: se prueban en test_pagos.py.
"""
from datetime import datetime

PRODUCTO = "plus"

# Estados en que el usuario tiene que tener Plus. CANCELED significa que no se va a renovar, pero el
# período ya pagado sigue corriendo hasta expiryTime. En período de gracia Google Play reintenta el
# cobro y el usuario conserva el acceso.
CON_ACCESO = {"SUBSCRIPTION_STATE_ACTIVE", "SUBSCRIPTION_STATE_IN_GRACE_PERIOD", "SUBSCRIPTION_STATE_CANCELED"}


def leer_fecha(texto: str) -> datetime:
    """Google devuelve RFC 3339 con hasta 9 decimales y 'Z'; Python 3.11 acepta solo hasta 6."""
    base, _, resto = texto.rstrip("Z").partition(".")
    return datetime.fromisoformat(f"{base}.{resto[:6].ljust(6, '0')}+00:00")


def plan_desde_suscripcion(sub: dict, token: str, ahora: datetime) -> dict | None:
    """Respuesta de purchases.subscriptionsv2.get → documento para Firestore. None si no es de Plus o no tiene cuenta."""
    items = [i for i in sub.get("lineItems", []) if i.get("productId") == PRODUCTO]
    uid = sub.get("externalAccountIdentifiers", {}).get("obfuscatedExternalAccountId")
    if not items or not uid:
        return None
    item = max(items, key=lambda i: leer_fecha(i["expiryTime"]))
    vence = leer_fecha(item["expiryTime"])
    estado = sub.get("subscriptionState", "")
    return {
        "uid": uid,
        "activo": estado in CON_ACCESO and vence > ahora,
        "vence": vence,
        "estado": estado,
        "plan": item.get("offerDetails", {}).get("basePlanId"),
        "renueva": item.get("autoRenewingPlan", {}).get("autoRenewEnabled", False),
        "token": token,
        "prueba": "testPurchase" in sub,  # compra de un tester de licencias: no genera cobro
    }


def debe_reemplazar(actual: dict | None, nuevo: dict) -> bool:
    """
    Al pasar de mensual a anual, Google Play crea una compra nueva y vence la anterior. Los avisos
    pueden llegar en cualquier orden: el vencimiento de la vieja no puede borrar el Plus de la nueva.
    Se escribe si no había nada, si el aviso es de la misma compra o si la nueva da acceso.
    """
    return actual is None or actual.get("token") == nuevo["token"] or nuevo["activo"]


def necesita_confirmacion(sub: dict) -> bool:
    """Google Play devuelve el dinero si la compra no se confirma en 3 días. La app la confirma; esto es el respaldo."""
    return sub.get("acknowledgementState") == "ACKNOWLEDGEMENT_STATE_PENDING"
