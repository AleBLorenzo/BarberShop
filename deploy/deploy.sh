#!/usr/bin/env bash
#
# BarberShop — build + redeploy idempotente (sin credenciales).
#
# Uso (en la VM, como usuario normal con sudo):
#   ./deploy.sh                    Reconstruye el backend y reinicia el servicio
#   ./deploy.sh --build-frontend   Además reconstruye el frontend con
#                                  VITE_API_BASE_URL=/api y lo publica en
#                                  /var/www/barbershop (usa esto cuando cambie
#                                  código del frontend)
#
# Variables opcionales (sobreescriben los valores por defecto):
#   APP_DIR   (default /opt/barbershop)
#   WWW_DIR   (default /var/www/barbershop)
#   SERVICE   (default barbershop-backend)
#
set -euo pipefail

APP_DIR="${APP_DIR:-/opt/barbershop}"
WWW_DIR="${WWW_DIR:-/var/www/barbershop}"
SERVICE="${SERVICE:-barbershop-backend}"

BUILD_FRONTEND=0
for arg in "$@"; do
    case "$arg" in
        --build-frontend) BUILD_FRONTEND=1 ;;
        -h|--help)
            sed -n '2,17p' "$0" | sed 's/^# \{0,1\}//'
            exit 0
            ;;
        *)
            echo "Argumento desconocido: $arg (usa --help)" >&2
            exit 2
            ;;
    esac
done

echo "==> Build backend (./mvnw -DskipTests package)"
cd "$APP_DIR/backend"
./mvnw -q -DskipTests package

if [ "$BUILD_FRONTEND" = "1" ]; then
    echo "==> Build frontend (VITE_API_BASE_URL=/api)"
    cd "$APP_DIR/frontend"
    npm ci
    VITE_API_BASE_URL=/api npm run build
    echo "==> Publicar estáticos en $WWW_DIR"
    rm -rf "$WWW_DIR"
    mkdir -p "$WWW_DIR"
    cp -R dist/. "$WWW_DIR/"
fi

echo "==> Reiniciar backend ($SERVICE)"
sudo systemctl restart "$SERVICE"

echo "==> Recargar nginx"
sudo systemctl reload nginx

echo "Deploy completado."
