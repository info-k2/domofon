#!/bin/sh
set -eu

LAN_CONF=/etc/nginx/nginx.lan.conf
FULL_TEMPLATE=/etc/nginx/edge.conf.template
OUT=/etc/nginx/nginx.conf

if [ -n "${PUBLIC_HOST:-}" ] && [ -f /etc/nginx/certs/fullchain.pem ] && [ -f /etc/nginx/certs/privkey.pem ]; then
  export PUBLIC_HOST
  echo "edge: :8080 + :8443 (domofon ${PUBLIC_HOST})"
  envsubst '${PUBLIC_HOST}' < "$FULL_TEMPLATE" > "$OUT"
else
  echo "edge: LAN mode :8080"
  cp "$LAN_CONF" "$OUT"
fi

nginx -t
exec nginx -g 'daemon off;'
