"""Pruebas del estado de Plus sin red ni Firestore:  python -m pytest test_pagos.py"""
from datetime import datetime, timezone

from pagos import debe_reemplazar, leer_fecha, necesita_confirmacion, plan_desde_suscripcion

AHORA = datetime(2026, 11, 10, 12, 0, tzinfo=timezone.utc)


def suscripcion(estado="SUBSCRIPTION_STATE_ACTIVE", vence="2026-12-10T12:00:00.123456789Z", uid="uid-ana", plan="mensual"):
    # Forma de la respuesta de purchases.subscriptionsv2.get de la API de Google Play.
    return {
        "subscriptionState": estado,
        "acknowledgementState": "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED",
        "externalAccountIdentifiers": {"obfuscatedExternalAccountId": uid},
        "lineItems": [{
            "productId": "plus",
            "expiryTime": vence,
            "autoRenewingPlan": {"autoRenewEnabled": True},
            "offerDetails": {"basePlanId": plan},
        }],
    }


def test_fecha_con_nanosegundos():
    assert leer_fecha("2026-12-10T12:00:00.123456789Z") == datetime(2026, 12, 10, 12, 0, 0, 123456, tzinfo=timezone.utc)
    assert leer_fecha("2026-12-10T12:00:00Z") == datetime(2026, 12, 10, 12, 0, tzinfo=timezone.utc)


def test_suscripcion_activa_da_plus():
    plan = plan_desde_suscripcion(suscripcion(), "t1", AHORA)
    assert plan["uid"] == "uid-ana" and plan["activo"] and plan["plan"] == "mensual" and not plan["prueba"]


def test_cancelada_conserva_plus_hasta_el_vencimiento():
    assert plan_desde_suscripcion(suscripcion("SUBSCRIPTION_STATE_CANCELED"), "t1", AHORA)["activo"]
    vencida = suscripcion("SUBSCRIPTION_STATE_CANCELED", vence="2026-11-01T00:00:00Z")
    assert not plan_desde_suscripcion(vencida, "t1", AHORA)["activo"]


def test_pago_rechazado_en_espera_saca_plus():
    assert not plan_desde_suscripcion(suscripcion("SUBSCRIPTION_STATE_ON_HOLD"), "t1", AHORA)["activo"]
    assert plan_desde_suscripcion(suscripcion("SUBSCRIPTION_STATE_IN_GRACE_PERIOD"), "t1", AHORA)["activo"]


def test_compra_sin_cuenta_o_de_otro_producto_se_ignora():
    assert plan_desde_suscripcion(suscripcion(uid=None), "t1", AHORA) is None
    otra = suscripcion()
    otra["lineItems"][0]["productId"] = "otro"
    assert plan_desde_suscripcion(otra, "t1", AHORA) is None


def test_el_vencimiento_de_la_compra_vieja_no_pisa_la_nueva():
    # Pasó de mensual (t-vieja) a anual (t-nueva) y el aviso de la vieja llegó después.
    actual = plan_desde_suscripcion(suscripcion(plan="anual", vence="2027-11-10T12:00:00Z"), "t-nueva", AHORA)
    vieja = plan_desde_suscripcion(suscripcion("SUBSCRIPTION_STATE_EXPIRED", vence="2026-11-10T11:00:00Z"), "t-vieja", AHORA)
    assert not debe_reemplazar(actual, vieja)
    # Pero si la que vence es la misma que estaba guardada, sí se saca el Plus.
    assert debe_reemplazar(vieja | {"token": "t-nueva"}, vieja | {"token": "t-nueva"})
    assert debe_reemplazar(None, vieja)


def test_confirmacion_pendiente():
    sub = suscripcion()
    assert not necesita_confirmacion(sub)
    sub["acknowledgementState"] = "ACKNOWLEDGEMENT_STATE_PENDING"
    assert necesita_confirmacion(sub)
