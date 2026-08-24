#!/bin/sh
set -eu

: "${TURN_USER:?TURN_USER is required}"
: "${TURN_PASSWORD:?TURN_PASSWORD is required}"
: "${EXTERNAL_IP:?EXTERNAL_IP is required (public IP)}"
: "${LAN_IP:?LAN_IP is required (PC LAN IP)}"
: "${TURN_REALM:=domofon}"

exec turnserver \
  -n \
  -v \
  --log-file=stdout \
  --listening-port=3478 \
  --tls-listening-port=5349 \
  --fingerprint \
  --use-auth-secret \
  --static-auth-secret="${TURN_PASSWORD}" \
  --realm="${TURN_REALM}" \
  --external-ip="${EXTERNAL_IP}" \
  --cert=/etc/coturn/certs/fullchain.pem \
  --pkey=/etc/coturn/certs/privkey.pem \
  --listening-ip=0.0.0.0 \
  --relay-ip=0.0.0.0 \
  --allowed-peer-ip=0.0.0.0-255.255.255.255
