#!/usr/bin/env bash
# Prueba de punta a punta contra la app corriendo: sube el CSV de ejemplo, espera al worker y revisa el resultado.
# Uso: scripts/smoke-test.sh [url-base]   (por defecto http://localhost:8080)
set -euo pipefail
BASE="${1:-http://localhost:8080}"

for i in $(seq 1 60); do
  curl -fs "$BASE/api/health" > /dev/null && break
  sleep 1
done
curl -fs "$BASE/api/health"
echo

ID=$(curl -fs -F "file=@ejemplos/actividad.csv;type=text/csv" "$BASE/api/uploads" | sed -E 's/.*"id":"([^"]+)".*/\1/')
echo "upload: $ID"

for i in $(seq 1 30); do
  ESTADO=$(curl -fs "$BASE/api/uploads/$ID" | sed -E 's/.*"status":"([A-Z]+)".*/\1/')
  echo "estado: $ESTADO"
  [ "$ESTADO" = "COMPLETED" ] || [ "$ESTADO" = "FAILED" ] && break
  sleep 1
done

DETALLE=$(curl -fs "$BASE/api/uploads/$ID")
echo "$DETALLE"
echo "$DETALLE" | grep -q '"status":"COMPLETED"'
echo "$DETALLE" | grep -q '"processedRows":6'
echo "$DETALLE" | grep -q '"errorRows":3'
curl -fs "$BASE/api/activity/by-month" | grep -q '"month":"2026-01"'
echo "OK: el CSV de ejemplo se procesó bien"
