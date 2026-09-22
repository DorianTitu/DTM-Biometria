# Despliegue de la reportería de demostración

La reportería se puede presentar con este Compose sin exponer PostgreSQL ni la API a Internet. React y la API comparten el mismo origen mediante Nginx, y la base de datos conserva su volumen al reiniciar.

## Preparar el VPS

Instala Docker Engine y el complemento Docker Compose en el VPS. Sube el repositorio completo, incluidos `docker-compose.demo.yml` y las carpetas `zkteco-admin-api`, `zkteco-admin-web` y `zkteco-email-notifier/db/init`. No subas archivos `.env` reales.

Desde la raíz del repositorio:

```bash
cp .env.demo.example .env.demo
```

Edita `.env.demo` y sustituye los tres valores de contraseña/clave por valores únicos. Puedes generar valores aleatorios en el servidor con:

```bash
openssl rand -hex 24
openssl rand -base64 48
```

Usa una contraseña para `DB_PASSWORD`, al menos 32 bytes aleatorios para `JWT_SECRET`, y una contraseña propia de 12 o más caracteres para `DEMO_ADMIN_PASSWORD`. Conserva `.env.demo` solo en el VPS y limita su lectura:

```bash
chmod 600 .env.demo
docker compose --env-file .env.demo -f docker-compose.demo.yml up -d --build
```

Abre TCP/80 en el firewall del VPS y visita `http://IP_DEL_VPS`. Inicia sesión con el usuario y contraseña de demo configurados en `.env.demo`. Para un dominio público, coloca HTTPS delante de este Nginx antes de compartir credenciales.

Para revisar el arranque:

```bash
docker compose --env-file .env.demo -f docker-compose.demo.yml ps
docker compose --env-file .env.demo -f docker-compose.demo.yml logs --tail=100 admin-api admin-web
```

Los registros del reporte son generados por la API y son ficticios. Las cuentas `admin.demo` e `inspector.demo` incluidas por el esquema inicial se desactivan al iniciar el modo demo. Para apagarlo:

```bash
docker compose --env-file .env.demo -f docker-compose.demo.yml down
```

`down` conserva la base de datos; no agregues `-v` si quieres mantenerla.
