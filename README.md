# DMT Biometría

Primera versión del panel de administración para el sistema de ingreso estudiantil con ZKTeco.

## Alcance de esta versión

- Login de administrador con JWT firmado por HMAC-SHA256.
- Autorización por rol `ADMINISTRATOR`.
- Gestión CRUD de estudiantes e inspectores.
- Cursos numerados del 1 al 13, sin paralelos.
- Inspector de curso con un curso asignado.
- Inspector de curso con uno o varios cursos asignados.
- Inspector general con acceso a todos los cursos.
- Dashboard de inspector con asistencia del día por curso.
- Configuración administrativa de hora de entrada y límite de atraso por curso.
- Una única entrada y una única salida canónicas por estudiante y día, con eventos crudos auditables.
- Cola persistente de notificaciones sin duplicados, preparada para integrar el envío de correos.
- Importación de Excel con el formato de `LISTADO_COMPLETO.xlsx`.
- Importación incremental y reemplazo completo con validación previa.
- ID biométrico único en creación, edición e importación.
- Correo opcional (`null`) para estudiantes sin representante registrado.
- Auditoría persistente de cada carga Excel en `import_runs`.
- Rate limit por IP, ruta y método: 10 logins por minuto y 120 operaciones por minuto.

## Ejecución local

Backend:

```bash
cd backend
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn main:app --reload --port 8000
```

Frontend, en otra terminal:

```bash
cd frontend
npm install
npm run dev
```

Credenciales demo:

```text
Usuario: admin.demo
Contraseña: admin12345
```

Los inspectores creados desde el panel reciben la contraseña definida en el formulario. Si se deja vacía al editar, la contraseña actual se conserva.

Para producción se debe cambiar `JWT_SECRET` y mover el rate limit a Redis o al proxy de entrada. La escucha del dispositivo ZKTeco y el envío de correos quedan fuera de esta primera migración.

## Persistencia PostgreSQL

`docker compose up -d --build` levanta PostgreSQL, backend y frontend. El backend inicializa `backend/db/schema.sql` al primer arranque y conserva los datos en el volumen `postgres_data`.

Servicios locales:

```text
Frontend:  http://localhost:5173
Backend:   http://localhost:8000
Health:    http://localhost:8000/api/health
Listener:  http://localhost:8085/iclock
```

PostgreSQL queda disponible dentro de la red de Compose (no se publica al host para evitar conflictos con otra instancia). Para abrir `psql`:

```bash
docker compose exec db psql -U dmt -d dmt_biometria
```

## Listener y notificaciones

El proyecto incluye el listener ADMS para ZKTeco y el worker de correo conectados al backend y a la cola persistente de notificaciones. Revise [la guía de integración](docs/zkteco-integration.md) antes de configurar dispositivos o credenciales SMTP.
