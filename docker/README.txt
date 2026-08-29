Domofon Docker — RTSP + MQTT-мост

Стек:
  mediamtx  — RTSP :8554 (камера → rtsp://IP:8554/door)
  bridge    — API входа, открытие двери через MQTT
  edge      — nginx :8080 → bridge (/health, /v1/*)

Папки:
  bridge/       — код моста
  bridge-data/  — данные моста (ключи, устройства)
  apk/          — domofon.apk для обновления приложения
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

Обновление приложения:
  1. Скопируйте APK: docker/apk/domofon.apk
  2. В .env: APP_VERSION, APP_VERSION_CODE, APP_PUBLIC_URL
  3. docker compose restart bridge
  Приложение после входа само предложит обновление.
