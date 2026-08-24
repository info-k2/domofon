#!/bin/sh
set -eu

: "${TURN_USER:?TURN_USER is required}"
: "${TURN_PASSWORD:?TURN_PASSWORD is required}"
: "${EXTERNAL_IP:?EXTERNAL_IP is required (public IP)}"
: "${LAN_IP:?LAN_IP is required (PC LAN IP)}"
: "${TURN_REALM:=domofon}"

exec turnserver \
  -n \
  --log-file=stdout \
  --listening-port=3478 \
  --tls-listening-port=5349 \
  --fingerprint \
  --lt-cred-mech \
  --realm="${TURN_REALM}" \
  --user="${TURN_USER}:${TURN_PASSWORD}" \
  --external-ip="${EXTERNAL_IP}/${LAN_IP}" \
  --cert=/etc/coturn/certs/fullchain.pem \
  --pkey=/etc/coturn/certs/privkey.pem \
  --listening-ip=0.0.0.0 \
  --relay-ip=0.0.0.0
