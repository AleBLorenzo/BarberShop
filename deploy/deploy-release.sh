#!/usr/bin/env bash
# BarberShop — instalación de un release en la VM (lo ejecuta GitHub Actions
# por SSH con sudo; el workflow sube a /tmp: backend.jar, dist.tar.gz, este
# script). Idempotente: se puede re-ejecutar sin dañar nada.
#
# Rutas fijas coherentes con deploy/README.md y barbershop-backend.service.
set -euo pipefail

REL=/tmp
JAR=/opt/barbershop/backend/target/backend-0.0.1-SNAPSHOT.jar
WEB=/var/www/barbershop
HEALTH='http://127.0.0.1:8080/api/public/servicios'

[ -f "$REL/backend.jar" ]   || { echo "ERROR: falta /tmp/backend.jar";   exit 1; }
[ -f "$REL/dist.tar.gz" ]   || { echo "ERROR: falta /tmp/dist.tar.gz";   exit 1; }

echo "==> 1/3 Backend: instalando jar"
install -D -m 644 "$REL/backend.jar" "$JAR"

echo "==> 2/3 Frontend: desempaquetado atómico en $WEB"
STAGE=$(mktemp -d)
tar -xzf "$REL/dist.tar.gz" -C "$STAGE"
rm -rf "${WEB}.prev"
[ -d "$WEB" ] && mv "$WEB" "${WEB}.prev"
mv "$STAGE" "$WEB"

echo "==> 3/3 Backend: reiniciar y comprobar salud"
systemctl restart barbershop-backend
for i in $(seq 1 30); do
  if curl -fsS "$HEALTH" > /dev/null 2>&1; then
    echo "OK: backend sano tras ~$((i * 2))s"
    exit 0
  fi
  sleep 2
done

echo "ERROR: el backend no respondió en 60s. Logs:"
journalctl -u barbershop-backend -n 100 --no-pager || true
exit 1
