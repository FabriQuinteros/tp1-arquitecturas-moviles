#!/usr/bin/env bash
# Corre un escenario de la prueba de carga contra la réplica local.
#   bash carga/correr.sh <nombre> <ruta> <replicas_api> <replicas_worker> [usuarios]
#   bash carga/correr.sh sincronico-1 /consumos/sincronico 1 1
#   bash carga/correr.sh asincronico-1 /consumos 1 1
#   bash carga/correr.sh asincronico-3 /consumos 3 3
set -euo pipefail
export MSYS_NO_PATHCONV=1 # que Git Bash en Windows no reescriba las rutas de los contenedores
cd "$(dirname "$0")/../backend"
NOMBRE=$1 RUTA=$2 API=$3 WORKER=$4 USUARIOS=${5:-200}
SALIDA=../carga/resultados/$NOMBRE
mkdir -p "$SALIDA"

# Base vacía en cada escenario: el emulador guarda todo en memoria.
docker compose up -d ${CONSTRUIR---build} --force-recreate --scale api="$API" --scale worker="$WORKER" >/dev/null
until docker compose run --rm iniciar >/dev/null 2>&1; do sleep 2; done # el emulador tarda en arrancar
docker compose run --rm api python -W ignore tareas.py cotizacion
docker compose restart balanceador >/dev/null # que nginx vea todas las réplicas
until curl -sf localhost:${PUERTO:-8000}/docs >/dev/null; do sleep 1; done

docker compose run --rm -d --name medidor -e FIRESTORE_EMULATOR_HOST=firestore:8080 \
  -v "$(cd ../carga && pwd -W 2>/dev/null || pwd)":/carga api python /carga/medir_cola.py "/carga/resultados/$NOMBRE/cola.csv" >/dev/null

# Foto del uso de CPU en pleno pico (100% = un núcleo), para ver qué está al límite.
(sleep 50; docker stats --no-stream --format "{{.Name}} {{.CPUPerc}}" > "$SALIDA/cpu.txt") &

docker run --rm --network backend_default -e RUTA="$RUTA" -e USUARIOS="$USUARIOS" \
  -v "$(cd ../carga && pwd -W 2>/dev/null || pwd)":/mnt/locust locustio/locust:2.32.4 \
  -f /mnt/locust/locustfile.py --host http://balanceador --headless --csv "/mnt/locust/resultados/$NOMBRE/locust" 2>&1 | tail -4

docker wait medidor >/dev/null
echo "gastos guardados: $(tail -1 "$SALIDA/cola.csv" | cut -d, -f2)"
