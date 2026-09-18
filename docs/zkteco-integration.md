# Integración ZKTeco y correo

El flujo conectado es:

```text
Biométrico → listener ADMS (puerto 8085) → RabbitMQ → procesador de eventos → API DMT → PostgreSQL / cola de notificaciones → servicio SMTP
```

El listener acepta el protocolo ADMS de ZKTeco en `/iclock`. Cada fila de `ATTLOG` o `RTLOG` se publica en la cola durable `dmt.attendance.events` con un identificador de origen estable. El procesador consume esa cola y la envía al backend. Si el backend está temporalmente fuera de servicio, RabbitMQ conserva el mensaje para reintentar.

`email-notifier` consulta la tabla `attendance_notifications`. Con `MAIL_ENABLED=false` permanece conectado, pero no reclama ni envía notificaciones. Para activar los correos, copie `.env.example` a `.env`, defina una clave de listener fuerte y complete SMTP.

```bash
cp .env.example .env
# editar .env y establecer MAIL_ENABLED=true
docker compose up -d --build
```

Configure cada biométrico con la IP del servidor y puerto `8085`. El servicio previo ocupa el puerto 8082 en este entorno, por eso el listener nuevo publica 8085.

La regla de cola es única por `fecha + estudiante + tipo (ENTRY/EXIT)`. Una segunda lectura, incluso desde otro dispositivo, no genera un segundo correo.
