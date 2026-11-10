"""Pruebas de la elección de avisos sin red ni Firestore:  python -m pytest test_retencion.py"""
from datetime import date
from decimal import Decimal

from retencion import dias_hasta_cierre, elegir_aviso, inactivo_para_campania, moneda, racha, resumen_mes

HOY = date(2026, 11, 12)  # jueves


def gasto(fecha, importe, division=(), borrado=False):
    return {"fecha": fecha, "importe": importe, "borrado": borrado,
            "division": [{"nombre": n, "porcentaje": p} for n, p in division]}


OCTUBRE = [
    gasto("2026-10-05", "30000.00", [("Ana", 50), ("Titular", 50)]),
    gasto("2026-10-20", "10000.00", [("Beto", 100)]),
    gasto("2026-10-21", "5000.00", borrado=True),
]


def test_moneda_como_en_la_app():
    assert moneda(Decimal("12345.6")) == "$ 12.345,60"
    assert moneda(Decimal("5")) == "$ 5,00"


def test_dias_hasta_el_cierre_igual_que_la_app():
    assert dias_hasta_cierre(15, HOY) == 3
    assert dias_hasta_cierre(11, HOY) == 29
    assert dias_hasta_cierre(31, HOY) == 18
    assert dias_hasta_cierre(5, date(2026, 12, 20)) == 16  # cruza el año


def test_racha_igual_que_la_app():
    assert racha([date(2026, 11, 10), date(2026, 11, 3), date(2026, 10, 26), date(2026, 10, 5)], HOY) == 3
    assert racha([date(2026, 11, 6), date(2026, 10, 28)], HOY) == 2


def test_resumen_del_mes_ignora_borrados():
    r = resumen_mes(OCTUBRE, 2026, 10)
    assert r["cantidad"] == 2 and r["total"] == Decimal("40000.00")
    assert r["quien_mas_debe"] == ("Ana", Decimal("15000.00"))


def test_aviso_de_cierre_tres_dias_antes():
    aviso = elegir_aviso({"cierre": True, "diaCierre": 15}, HOY, None, [], HOY)
    assert aviso["tipo"] == "cierre" and aviso["destino"] == "deudas"
    assert elegir_aviso({"cierre": True, "diaCierre": 16}, HOY, None, [], HOY) is None
    assert elegir_aviso({"cierre": False, "diaCierre": 15}, HOY, None, [], HOY) is None


def test_resumen_el_dia_uno():
    aviso = elegir_aviso(None, date(2026, 11, 1), None, OCTUBRE, date(2026, 11, 1))
    assert aviso["titulo"] == "Tu resumen de octubre"
    assert aviso["texto"] == "Gastaste $ 40.000,00 en 2 gastos. Ana te debe $ 15.000,00."
    # Sin gastos el mes anterior, no hay nada que contar.
    assert elegir_aviso(None, date(2026, 11, 1), None, [], date(2026, 11, 1)) is None


def test_el_cierre_tiene_prioridad_sobre_el_resumen():
    aviso = elegir_aviso({"cierre": True, "diaCierre": 4}, date(2026, 11, 1), None, OCTUBRE, date(2026, 11, 1))
    assert aviso["tipo"] == "cierre"


def test_recordatorio_por_inactividad_con_racha():
    gastos = [gasto("2026-11-03", "100"), gasto("2026-10-28", "100")]
    # Abrió la app el 4: el 10 van 6 días, todavía no; el 11 se cumple la semana.
    assert elegir_aviso(None, date(2026, 11, 4), None, gastos, date(2026, 11, 10)) is None
    aviso = elegir_aviso(None, date(2026, 11, 4), None, gastos, date(2026, 11, 11))
    assert aviso["tipo"] == "inactividad" and aviso["destino"] == "nuevo_gasto"
    assert "racha de 2 semanas" in aviso["texto"]
    assert aviso["recordatoriosSinRespuesta"] == 1


def test_quien_abre_la_app_no_recibe_recordatorio():
    assert elegir_aviso(None, date(2026, 11, 10), None, [], HOY) is None


def test_no_se_insiste_mas_de_tres_veces_ni_dos_por_semana():
    abrio = date(2026, 10, 1)
    # Ya se le mandaron 3 sin que abra la app: no se insiste más.
    assert elegir_aviso(None, abrio, {"ultimo": "2026-11-01", "recordatoriosSinRespuesta": 3}, [], HOY) is None
    # Se le mandó uno hace 4 días: todavía no.
    assert elegir_aviso(None, abrio, {"ultimo": "2026-11-08", "recordatoriosSinRespuesta": 1}, [], HOY) is None
    # Hace 7 días: el segundo.
    assert elegir_aviso(None, abrio, {"ultimo": "2026-11-05", "recordatoriosSinRespuesta": 1}, [], HOY)["recordatoriosSinRespuesta"] == 2


def test_si_volvio_a_abrir_el_contador_arranca_de_cero():
    estado = {"ultimo": "2026-10-01", "recordatoriosSinRespuesta": 3}
    assert elegir_aviso(None, date(2026, 10, 2), estado, [], HOY)["recordatoriosSinRespuesta"] == 1


def test_las_preferencias_apagan_cada_aviso():
    apagado = {"resumen": False, "inactividad": False}
    assert elegir_aviso(apagado, date(2026, 10, 1), None, OCTUBRE, date(2026, 11, 1)) is None


def test_segmento_de_reactivacion():
    assert inactivo_para_campania(date(2026, 10, 29), HOY)
    assert not inactivo_para_campania(date(2026, 11, 1), HOY)
    assert not inactivo_para_campania(None, HOY)  # nunca abrió con esta versión: no sabemos
