from __future__ import annotations

import json
import logging
import os
import secrets
import threading
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
LAN_IP = os.getenv("LAN_IP", "").strip()


def stream_url_for_login() -> str:
    if STREAM_URL:
        return STREAM_URL
    if LAN_IP:
        return f"rtsp://{LAN_IP}:8554/door"
    raise HTTPException(500, "STREAM_URL or LAN_IP is not configured")


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

app = FastAPI(title="Domofon bridge", version="0.8.4")


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
    url = stream_url_for_login() if (STREAM_URL or LAN_IP) else None
    return {
        "status": "ok",
        "stream_url": url,
        "mqtt": MQTT_HOST,
        "door_topic": MQTT_TOPIC_DOOR,
        "ring_topic": MQTT_TOPIC_RING,
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
    url = stream_url_for_login()
    log.info("login stream_url=%s", url)
    return {
        "api_key": API_KEY,
        "stream_url": url,
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
