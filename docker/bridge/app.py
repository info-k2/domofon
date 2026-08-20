from __future__ import annotations

import json
import os
import secrets
from ipaddress import ip_address
from pathlib import Path
from typing import Any

import httpx
from fastapi import Depends, FastAPI, Header, HTTPException, Request
from pydantic import BaseModel, Field

DATA_DIR = Path(os.getenv("DATA_DIR", "/data"))
DEVICES_FILE = DATA_DIR / "devices.json"
API_KEY_FILE = DATA_DIR / "api_key"
HA_BASE_URL = os.getenv("HA_BASE_URL", "").rstrip("/")
HA_TOKEN = os.getenv("HA_TOKEN", "")
HA_ENTITY_ID = os.getenv("HA_ENTITY_ID", "input_button.domofon")


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

app = FastAPI(title="Domofon bridge", version="0.4.1")


class DeviceIn(BaseModel):
    push_token: str = Field(min_length=8)
    name: str = "android"


def is_lan_ip(value: str) -> bool:
    try:
        addr = ip_address(value)
    except ValueError:
        return False
    return bool(addr.is_private or addr.is_loopback)


def request_is_from_lan(request: Request, x_forwarded_for: str | None) -> bool:
    direct = request.client.host if request.client else ""
    if not is_lan_ip(direct):
        return False
    if not x_forwarded_for:
        return True
    forwarded = x_forwarded_for.split(",", 1)[0].strip()
    return is_lan_ip(forwarded)


def require_api_key(x_api_key: str | None = Header(default=None)) -> None:
    if not API_KEY:
        raise HTTPException(500, "API_KEY is not configured on the server")
    if not x_api_key or x_api_key != API_KEY:
        raise HTTPException(401, "Invalid API key")


def ha_service(entity_id: str) -> tuple[str, str]:
    domain = entity_id.split(".", 1)[0] if "." in entity_id else "switch"
    if domain in {"input_button", "button"}:
        return domain, "press"
    if domain == "lock":
        return domain, "unlock"
    return domain, "turn_on"


def load_devices() -> list[dict[str, Any]]:
    if not DEVICES_FILE.exists():
        return []
    return json.loads(DEVICES_FILE.read_text(encoding="utf-8"))


def save_devices(devices: list[dict[str, Any]]) -> None:
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    DEVICES_FILE.write_text(json.dumps(devices, ensure_ascii=False, indent=2), encoding="utf-8")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.get("/v1/pair")
def pair(
    request: Request,
    x_forwarded_for: str | None = Header(default=None),
) -> dict[str, str]:
    if not request_is_from_lan(request, x_forwarded_for):
        raise HTTPException(403, "Ключ выдаётся только из домашней сети")
    return {"api_key": API_KEY}


@app.post("/v1/door/open")
async def open_door(_: None = Depends(require_api_key)) -> dict[str, str]:
    if not HA_BASE_URL or not HA_TOKEN or not HA_ENTITY_ID:
        raise HTTPException(500, "Home Assistant is not configured")
    domain, service = ha_service(HA_ENTITY_ID)
    url = f"{HA_BASE_URL}/api/services/{domain}/{service}"
    headers = {
        "Authorization": f"Bearer {HA_TOKEN}",
        "Content-Type": "application/json",
    }
    payload = {"entity_id": HA_ENTITY_ID}
    try:
        async with httpx.AsyncClient(timeout=12.0) as client:
            response = await client.post(url, headers=headers, json=payload)
    except httpx.HTTPError as exc:
        raise HTTPException(502, f"Home Assistant unreachable: {exc}") from exc
    if response.status_code >= 400:
        raise HTTPException(502, f"Home Assistant HTTP {response.status_code}: {response.text[:180]}")
    return {"status": "opened"}


@app.post("/v1/devices")
def register_device(body: DeviceIn, _: None = Depends(require_api_key)) -> dict[str, str]:
    devices = [item for item in load_devices() if item.get("push_token") != body.push_token]
    devices.append({"push_token": body.push_token, "name": body.name})
    save_devices(devices)
    return {"status": "registered", "devices": str(len(devices))}


@app.post("/v1/ring")
def ring(_: None = Depends(require_api_key)) -> dict[str, Any]:
    devices = load_devices()
    return {"status": "queued", "recipients": len(devices)}
