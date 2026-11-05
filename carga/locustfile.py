"""Prueba de carga: muchos titulares mandando consumos a la vez.

RUTA elige qué se prueba: /consumos (asincrónico, con cola) o /consumos/sincronico.
La forma de la carga es un pico: de 0 a USUARIOS en pocos segundos, como el día del cierre.
"""
import os
import random
from datetime import date

from locust import HttpUser, LoadTestShape, between, task

RUTA = os.environ.get("RUTA", "/consumos")
USUARIOS = int(os.environ.get("USUARIOS", "200"))
COMERCIOS = ["Supermercado", "Farmacia", "Combustible", "Netflix", "Spotify", "Ropa", "Restaurante"]


class Titular(HttpUser):
    wait_time = between(0.2, 1.0)

    def on_start(self):
        self.uid = f"carga-{random.randrange(1000)}"

    @task
    def cargar_consumo(self):
        dolares = random.random() < 0.1
        self.client.post(RUTA, name=RUTA, json={
            "uid": self.uid,
            "comercio": random.choice(COMERCIOS),
            "importe": f"{random.uniform(5, 200) if dolares else random.uniform(1000, 90000):.2f}",
            "moneda": "USD" if dolares else "ARS",
            "fecha": date.today().isoformat(),
        })


class Pico(LoadTestShape):
    # (hasta el segundo, usuarios, cuántos se suman por segundo)
    etapas = [(10, USUARIOS // 10, 10), (20, USUARIOS, USUARIOS), (80, USUARIOS, USUARIOS), (90, 0, USUARIOS)]

    def tick(self):
        t = self.get_run_time()
        return next(((u, r) for fin, u, r in self.etapas if t < fin), None)
