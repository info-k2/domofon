Сюда положите TLS-сертификат с SAN на PUBLIC_HOST и TURN_HOST:

  fullchain.pem
  privkey.pem

Варианты:
1) Скопировать с Synology (Let's Encrypt), если есть wildcard или оба имени.
2) Сгенерировать самоподписанный для теста:

   powershell -File docker/scripts/gen-certs.ps1

В приложении Android для самоподписанного HTTPS нужен доверенный CA
или временно использовать только LAN http://IP:8080 (без mux).
