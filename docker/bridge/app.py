from __future__ import annotations

import base64
import hashlib
import hmac
import json
import logging
import os
import secrets
import threading
import time
from pathlib import Path
from typing import Any

import paho.mqtt.client as mqtt
from fastapi import Depends, FastAPI, Header, HTTPException
from pydantic import BaseModel, Field

logging.basicConfig(level=logging.INFO)
log = logging.getLogger("domofon-bridge")

DATA_DIR = Path(os.getenv("DATA_DIR", "/data"))
DEVICES_FILE = DATA_DIR / "devices.json"
API_KEY_FILE = DATA_DIR / "api_key"

BRIDGE_USER = os.getenv("BRIDGE_USER", "").strip()
BRIDGE_PASSWORD = os.getenv("BRIDGE_PASSWORD", "").strip()

MQTT_HOST = os.getenv("MQTT_HOST", "").strip()
MQTT_PORT = int(os.getenv("MQTT_PORT", "1883"))
MQTT_USER = os.getenv("MQTT_USER", "").strip()
MQTT_PASSWORD = os.getenv("MQTT_PASSWORD", "").strip()
MQTT_TOPIC_DOOR = os.getenv("MQTT_TOPIC_DOOR", "domofon/door/open")
MQTT_TOPIC_RING = os.getenv("MQTT_TOPIC_RING", "domofon/ring")
STREAM_URL = os.getenv("STREAM_URL", "").strip()
STREAM_URL_LAN = os.getenv("STREAM_URL_LAN", "").strip()
VIDEO_MODE = os.getenv("VIDEO_MODE", "internet").strip().lower()
TURN_URL = os.getenv("TURN_URL", "").strip()
TURN_USER = os.getenv("TURN_USER", "").strip()
TURN_PASSWORD = os.getenv("TURN_PASSWORD", "").strip()


def turn_rest_credentials(username: str, secret: str, ttl: int = 86_400) -> tuple[str, str]:
    """Coturn use-auth-secret: username = expiry:user, credential = base64(hmac-sha1)."""
    expiry = int(time.time()) + ttl
    turn_user = f"{expiry}:{username}"
    digest = hmac.new(secret.encode(), turn_user.encode(), hashlib.sha1).digest()
    return turn_user, base64.b64encode(digest).decode()


def stream_url_for_mode() -> str:
    if VIDEO_MODE == "lan":
        lan = STREAM_URL_LAN or (
            f"http://{os.getenv('LAN_IP', '').strip()}:8080/door/whep"
            if os.getenv("LAN_IP", "").strip()
            else ""
        )
        if not lan:
            raise HTTPException(500, "STREAM_URL_LAN or LAN_IP is not configured")
        return lan
    if not STREAM_URL:
        raise HTTPException(500, "STREAM_URL is not configured")
    return STREAM_URL


def ice_servers() -> list[dict[str, Any]]:
    if VIDEO_MODE == "lan":
        log.info("video_mode=lan: direct WebRTC, no TURN")
        return [{"urls": ["stun:stun.l.google.com:19302"]}]

    servers: list[dict[str, Any]] = [
        {"urls": ["stun:stun.l.google.com:19302"]},
    ]
    if not (TURN_URL and TURN_USER and TURN_PASSWORD):
        return servers

    turn_user, turn_cred = turn_rest_credentials(TURN_USER, TURN_PASSWORD)
    turn_entry = {"username": turn_user, "credential": turn_cred}

    lan_ip = os.getenv("LAN_IP", "").strip()
    if VIDEO_MODE in ("turn", "internet") and lan_ip:
        servers.append({**turn_entry, "urls": [f"turn:{lan_ip}:3478?transport=tcp"]})
    if VIDEO_MODE == "internet":
        servers.append({**turn_entry, "urls": [TURN_URL]})

    log.info("video_mode=%s ice_servers=%s", VIDEO_MODE, len(servers))
    return servers


def load_or_create_api_key() -> str:
    env_key = os.getenv("API_KEY", "").strip()
    if env_key:
        return env_key
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    if API_KEY_FILE.exists():
        stored = API_KEY_FILE.read_text(encoding="utf-8").strip()
        if stored:
            return stored
    key = secrets.token_hex(20)
    API_KEY_FILE.write_text(key, encoding="utf-8")
    return key


API_KEY = load_or_create_api_key()
mqtt_client: mqtt.Client | None = None
mqtt_lock = threading.Lock()
last_ring: dict[str, Any] | None = None

app = FastAPI(title="Domofon bridge", version="0.7.0")


class LoginIn(BaseModel):
    username: str
    password: str


class DeviceIn(BaseModel):
    push_token: str = Field(min_length=8)
    name: str = "android"


def require_api_key(x_api_key: str | None = Header(default=None)) -> None:
    if not API_KEY:
        raise HTTPException(500, "API_KEY is not configured on the server")
    if not x_api_key or x_api_key != API_KEY:
        raise HTTPException(401, "Invalid API key")


def load_devices() -> list[dict[str, Any]]:
    if not DEVICES_FILE.exists():
        return []
    return json.loads(DEVICES_FILE.read_text(encoding="utf-8"))


def save_devices(devices: list[dict[str, Any]]) -> None:
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    DEVICES_FILE.write_text(json.dumps(devices, ensure_ascii=False, indent=2), encoding="utf-8")


def on_mqtt_connect(client: mqtt.Client, _userdata: Any, _flags: dict[str, Any], reason_code: int, _properties: Any = None) -> None:
    if reason_code != 0:
        log.error("MQTT connect failed: %s", reason_code)
        return
    client.subscribe(MQTT_TOPIC_RING)
    log.info("MQTT connected, subscribed to %s", MQTT_TOPIC_RING)


def on_mqtt_message(_client: mqtt.Client, _userdata: Any, msg: mqtt.MQTTMessage) -> None:
    global last_ring
    payload = msg.payload.decode("utf-8", errors="replace")
    devices = load_devices()
    last_ring = {
        "topic": msg.topic,
        "payload": payload,
        "recipients": len(devices),
    }
    log.info("Ring via MQTT (%s recipients): %s", len(devices), payload[:120])
    # FCM push will be wired here later.


def start_mqtt() -> mqtt.Client:
    if not MQTT_HOST:
        raise RuntimeError("MQTT_HOST is not configured")
    client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id="domofon-bridge")
    if MQTT_USER:
        client.username_pw_set(MQTT_USER, MQTT_PASSWORD)
    client.on_connect = on_mqtt_connect
    client.on_message = on_mqtt_message
    client.connect(MQTT_HOST, MQTT_PORT, keepalive=30)
    client.loop_start()
    return client


@app.on_event("startup")
def startup() -> None:
    global mqtt_client
    mqtt_client = start_mqtt()


@app.on_event("shutdown")
def shutdown() -> None:
    global mqtt_client
    if mqtt_client is not None:
        mqtt_client.loop_stop()
        mqtt_client.disconnect()
        mqtt_client = None


@app.get("/health")
def health() -> dict[str, Any]:
    return {
        "status": "ok",
        "video_mode": VIDEO_MODE,
        "mqtt": MQTT_HOST,
        "door_topic": MQTT_TOPIC_DOOR,
        "ring_topic": MQTT_TOPIC_RING,
        "stream_url": stream_url_for_mode() if STREAM_URL or STREAM_URL_LAN else None,
        "turn": bool(TURN_URL) and VIDEO_MODE != "lan",
        "last_ring": last_ring,
    }


@app.post("/v1/login")
def login(body: LoginIn) -> dict[str, Any]:
    if not BRIDGE_USER or not BRIDGE_PASSWORD:
        raise HTTPException(500, "BRIDGE_USER / BRIDGE_PASSWORD are not configured")
    user_ok = secrets.compare_digest(body.username, BRIDGE_USER)
    pass_ok = secrets.compare_digest(body.password, BRIDGE_PASSWORD)
    if not (user_ok and pass_ok):
        raise HTTPException(401, "Неверный логин или пароль")
    url = stream_url_for_mode()
    return {
        "api_key": API_KEY,
        "stream_url": url,
        "video_mode": VIDEO_MODE,
        "ice_servers": ice_servers(),
    }


@app.post("/v1/door/open")
def open_door(_: None = Depends(require_api_key)) -> dict[str, str]:
    if mqtt_client is None:
        raise HTTPException(503, "MQTT is not connected")
    payload = json.dumps({"source": "app"})
    with mqtt_lock:
        info = mqtt_client.publish(MQTT_TOPIC_DOOR, payload, qos=1)
    if info.rc != mqtt.MQTT_ERR_SUCCESS:
        raise HTTPException(502, f"MQTT publish failed: {info.rc}")
    return {"status": "opened"}


@app.post("/v1/devices")
def register_device(body: DeviceIn, _: None = Depends(require_api_key)) -> dict[str, str]:
    devices = [item for item in load_devices() if item.get("push_token") != body.push_token]
    devices.append({"push_token": body.push_token, "name": body.name})
    save_devices(devices)
    return {"status": "registered", "devices": str(len(devices))}
