# ZKTeco Auth Listener

Listener Spring Boot independiente para ZKTeco ADMS/iClock. Solo procesa `ATTLOG`/`RTLOG` y escribe cada autenticación en el log de Docker.

El dispositivo debe apuntar al host donde corre Docker, puerto `8083` (el contenedor escucha internamente en `8082`). No es un WebSocket de entrada: ADMS entrega los eventos por HTTP PUSH.

## Ejecutar

```bash
mvn clean package
docker compose up -d --build
docker logs -f zkteco-auth-listener
```

Ejemplo de log:

```text
[AUTH_OK] eventId=1 userId=1 device=VDE2261000139 authenticatedAt=2026-08-31T21:43:35-05:00 verifyType=1 status=255
```
