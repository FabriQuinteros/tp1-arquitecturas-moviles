"""Cuánto tarda el worker en vaciar la cola después del pico: cuenta los gastos en Firestore hasta que se estabilizan.

    python medir_cola.py salida.csv
"""
import csv
import os
import sys
import time

from google.cloud import firestore

os.environ.setdefault("FIRESTORE_EMULATOR_HOST", "localhost:8081")
db = firestore.Client(project="tarjetazo-local")
gastos = db.collection_group("gastos")

with open(sys.argv[1], "w", newline="") as f:
    salida = csv.writer(f)
    salida.writerow(["segundo", "gastos_guardados"])
    inicio, anterior, quietos = time.time(), -1, 0
    while quietos < 15:  # 15 segundos sin cambios: la cola está vacía
        n = gastos.count().get()[0][0].value
        salida.writerow([round(time.time() - inicio), n])
        f.flush()
        quietos = quietos + 1 if n == anterior and n > 0 else 0
        anterior = n
        time.sleep(1)
print("gastos guardados:", anterior)
