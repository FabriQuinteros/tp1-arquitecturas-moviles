"""Qué aviso le corresponde hoy a cada usuario (TP 6).

La tarea diaria (tareas.py avisos) recorre las cuentas con algún teléfono registrado y, para cada una,
elige como mucho un aviso: el de cierre, el resumen del mes o el recordatorio por inactividad, en ese
orden de prioridad. Las reglas son las mismas que usa la app (Retencion.kt).

Nada de este archivo toca la red ni Firestore: se prueba en test_retencion.py.
"""
import calendar
from datetime import date, timedelta
from decimal import ROUND_DOWN, Decimal

TITULAR = "Titular"
MESES = ["enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto",
         "septiembre", "octubre", "noviembre", "diciembre"]

DIAS_ANTES_DEL_CIERRE = 3
DIAS_SIN_ABRIR = 7          # recién a la semana se considera que el usuario dejó de usar la app
RECORDATORIOS_MAXIMOS = 3   # sin respuesta: después de tres, se deja de insistir

DEFAULTS = {"cierre": False, "diaCierre": None, "resumen": True, "inactividad": True, "novedades": True}


def preferencias(avisos: dict | None) -> dict:
    return DEFAULTS | {k: v for k, v in (avisos or {}).items() if k in DEFAULTS}


def moneda(n: Decimal) -> str:
    """12345.6 → '$ 12.345,60', igual que la app."""
    entero, _, dec = f"{n:,.2f}".partition(".")
    return f"$ {entero.replace(',', '.')},{dec}"


def dias_hasta_cierre(dia_cierre: int, hoy: date) -> int:
    def cierre_en(anio, mes):
        return date(anio, mes, min(dia_cierre, calendar.monthrange(anio, mes)[1]))
    este = cierre_en(hoy.year, hoy.month)
    if este < hoy:
        siguiente = date(hoy.year + hoy.month // 12, hoy.month % 12 + 1, 1)
        este = cierre_en(siguiente.year, siguiente.month)
    return (este - hoy).days


def racha(fechas: list[date], hoy: date) -> int:
    """Semanas seguidas con algún gasto; la semana en curso puede estar vacía hasta el domingo."""
    lunes = lambda d: d - timedelta(days=d.weekday())
    semanas = {lunes(f) for f in fechas if f <= hoy}
    semana = lunes(hoy)
    if semana not in semanas:
        semana -= timedelta(weeks=1)
    cuenta = 0
    while semana in semanas:
        cuenta += 1
        semana -= timedelta(weeks=1)
    return cuenta


def repartir(importe: Decimal, division: list[dict]) -> list[tuple[str, Decimal]]:
    """Igual que la app: cada parte se trunca al centavo y el resto va a la primera persona."""
    if not division:
        return [(TITULAR, importe)]
    partes = [(p["nombre"], (importe * p["porcentaje"] / 100).quantize(Decimal("0.01"), ROUND_DOWN)) for p in division]
    resto = importe - sum(p for _, p in partes)
    return [(n, p + resto if i == 0 else p) for i, (n, p) in enumerate(partes)]


def resumen_mes(gastos: list[dict], anio: int, mes: int) -> dict:
    """gastos: documentos de Firestore {fecha, importe (texto), division, borrado}."""
    del_mes = [g for g in gastos if not g.get("borrado") and g["fecha"].startswith(f"{anio:04d}-{mes:02d}")]
    deudas: dict[str, Decimal] = {}
    for g in del_mes:
        for nombre, parte in repartir(Decimal(g["importe"]), g.get("division") or []):
            deudas[nombre] = deudas.get(nombre, Decimal(0)) + parte
    otros = {n: v for n, v in deudas.items() if n != TITULAR}
    return {
        "cantidad": len(del_mes),
        "total": sum((Decimal(g["importe"]) for g in del_mes), Decimal(0)),
        "quien_mas_debe": max(otros.items(), key=lambda kv: kv[1]) if otros else None,
    }


def elegir_aviso(avisos: dict | None, ultima_apertura: date | None, estado: dict | None, gastos: list[dict], hoy: date) -> dict | None:
    """
    Como mucho un aviso por día y por usuario. Devuelve {tipo, titulo, texto, destino} o None.
    estado: lo que la tarea guardó la vez anterior {ultimo: fecha ISO, recordatoriosSinRespuesta: n}.
    """
    p = preferencias(avisos)
    estado = estado or {}

    if p["cierre"] and p["diaCierre"] and dias_hasta_cierre(int(p["diaCierre"]), hoy) == DIAS_ANTES_DEL_CIERRE:
        return {
            "tipo": "cierre",
            "titulo": "Tu tarjeta cierra en 3 días",
            "texto": "Revisá que estén todos los gastos y cómo los dividiste, así el resumen sale bien.",
            "destino": "deudas",
        }

    if p["resumen"] and hoy.day == 1:
        anterior = hoy - timedelta(days=1)
        r = resumen_mes(gastos, anterior.year, anterior.month)
        if r["cantidad"]:
            texto = f"Gastaste {moneda(r['total'])} en {r['cantidad']} {'gasto' if r['cantidad'] == 1 else 'gastos'}."
            if r["quien_mas_debe"]:
                nombre, monto = r["quien_mas_debe"]
                texto += f" {nombre} te debe {moneda(monto)}."
            return {"tipo": "resumen", "titulo": f"Tu resumen de {MESES[anterior.month - 1]}", "texto": texto, "destino": "deudas"}

    if p["inactividad"] and ultima_apertura and (hoy - ultima_apertura).days >= DIAS_SIN_ABRIR:
        # Si abrió la app después del último recordatorio, el contador vuelve a cero.
        ultimo = date.fromisoformat(estado["ultimo"]) if estado.get("ultimo") else None
        sin_respuesta = 0 if (ultimo is None or ultima_apertura > ultimo) else estado.get("recordatoriosSinRespuesta", 0)
        ya_avisado_esta_semana = ultimo is not None and (hoy - ultimo).days < DIAS_SIN_ABRIR
        if sin_respuesta < RECORDATORIOS_MAXIMOS and not ya_avisado_esta_semana:
            semanas = racha([date.fromisoformat(g["fecha"]) for g in gastos if not g.get("borrado")], hoy)
            texto = (f"No pierdas tu racha de {semanas} semanas: anotá lo que gastaste estos días."
                     if semanas >= 2 else "¿Usaste la tarjeta esta semana? Anotalo ahora y a fin de mes no tenés que adivinar.")
            return {"tipo": "inactividad", "titulo": "¿Te quedó algún gasto sin anotar?", "texto": texto,
                    "destino": "nuevo_gasto", "recordatoriosSinRespuesta": sin_respuesta + 1}
    return None


def inactivo_para_campania(ultima_apertura: date | None, hoy: date, dias: int = 14) -> bool:
    """Segmento de la campaña de reactivación: cuentas que no abren la app hace dos semanas o más."""
    return ultima_apertura is not None and (hoy - ultima_apertura).days >= dias
