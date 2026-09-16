# DMT Biometría

Primera versión del panel de administración para el sistema de ingreso estudiantil con ZKTeco.

## Alcance de esta versión

- Login de administrador con JWT firmado por HMAC-SHA256.
- Autorización por rol `ADMINISTRATOR`.
- Gestión CRUD de estudiantes e inspectores.
- Cursos numerados del 1 al 13, sin paralelos.
- Inspector de curso con un curso asignado.
- Inspector general con acceso a todos los cursos.
- Importación de Excel con el formato de `LISTADO_COMPLETO.xlsx`.
- Importación incremental y reemplazo completo con validación previa.
- ID biométrico único en creación, edición e importación.
- Correo opcional (`null`) para estudiantes sin representante registrado.
- Historial en memoria de cargas realizadas durante la sesión del backend.
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

Para producción se debe cambiar `JWT_SECRET`, sustituir el almacenamiento en memoria por una base de datos y mover el rate limit a Redis o al proxy de entrada.
