Domofon Docker — RTSP + MQTT-мост

Стек:
  mediamtx  — RTSP :8554 (камера → rtsp://IP:8554/door)
  bridge    — API входа, открытие двери через MQTT
  edge      — nginx :8080 → bridge (/health, /v1/*)

Папки:
  bridge/       — код моста
  bridge-data/  — данные моста (ключи, устройства), создаётся автоматически
  nginx/        — конфиг reverse proxy

Быстрый старт:
  1. cp mediamtx.yml.example mediamtx.yml   # камера
  2. cp .env.example .env                   # MQTT, пароли, STREAM_URL
  3. docker compose up -d

Приложение:
  Адрес моста: http://192.168.0.34:8080
  После входа: RTSP rtsp://192.168.0.34:8554/door

Интернет (опционально):
  Проброс на роутере: 8080 (API), 8554/TCP (RTSP)
