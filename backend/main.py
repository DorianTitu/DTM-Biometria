from __future__ import annotations

import base64
import hashlib
import hmac
import json
import os
import time
from collections import defaultdict, deque
from datetime import datetime, timedelta, timezone
from io import BytesIO
from typing import Any

from fastapi import Depends, FastAPI, File, HTTPException, Request, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

SECRET = os.getenv("JWT_SECRET", "dmt-demo-secret-change-in-production").encode()
JWT_TTL = 60 * 60 * 8
COURSES = [
    {"id": i, "name": name} for i, name in enumerate([
        "1ro BASICA", "2do BASICA", "3ro BASICA", "4to BASICA", "5to BASICA", "6to BASICA",
        "7mo BASICA", "8vo BASICA", "9no BASICA", "10mo BASICA", "1ro BACH", "2do BACH", "3ro BACH"
    ], 1)
]
students: list[dict[str, Any]] = [
    {"id": 1, "biometric_id": "1001", "first_name": "JOAN SEBASTIAN", "last_name": "ARMIJOS TORRES", "course_id": 1, "email": "dyaniliz@hotmail.com", "active": True},
    {"id": 2, "biometric_id": "1002", "first_name": "MARIA JOSE", "last_name": "CASTRO VEGA", "course_id": 8, "email": None, "active": True},
    {"id": 3, "biometric_id": "1003", "first_name": "DANIEL", "last_name": "MOLINA ROJAS", "course_id": 11, "email": "familia@example.com", "active": True},
]
inspectors: list[dict[str, Any]] = [
    {"id": 1, "first_name": "Ana", "last_name": "Vega", "username": "ana.inspectora", "email": "ana@colegio.edu.ec", "type": "CURSO", "course_ids": [1, 2], "active": True},
    {"id": 2, "first_name": "Luis", "last_name": "Mora", "username": "luis.general", "email": "luis@colegio.edu.ec", "type": "GENERAL", "course_ids": list(range(1, 14)), "active": True},
]
next_student_id = 4
next_inspector_id = 3
load_history: list[dict[str, Any]] = []

def _b64(value: bytes) -> str:
    return base64.urlsafe_b64encode(value).rstrip(b"=").decode()

def _json_b64(value: dict) -> str:
    return _b64(json.dumps(value, separators=(",", ":")).encode())

def make_token(username: str, role: str) -> str:
    header = _json_b64({"alg": "HS256", "typ": "JWT"})
    payload = _json_b64({"sub": username, "role": role, "exp": int(time.time()) + JWT_TTL})
    signature = _b64(hmac.new(SECRET, f"{header}.{payload}".encode(), hashlib.sha256).digest())
    return f"{header}.{payload}.{signature}"

def decode_token(token: str) -> dict:
    try:
        header, payload, signature = token.split(".")
        expected = _b64(hmac.new(SECRET, f"{header}.{payload}".encode(), hashlib.sha256).digest())
        if not hmac.compare_digest(signature, expected): raise ValueError()
        data = json.loads(base64.urlsafe_b64decode(payload + "=" * (-len(payload) % 4)))
        if data["exp"] < time.time(): raise ValueError()
        return data
    except Exception as exc:
        raise HTTPException(401, "Token inválido o expirado") from exc

def current_admin(request: Request) -> dict:
    auth = request.headers.get("Authorization", "")
    if not auth.startswith("Bearer "): raise HTTPException(401, "Autenticación requerida")
    user = decode_token(auth[7:])
    if user.get("role") != "ADMINISTRATOR": raise HTTPException(403, "Se requiere rol administrador")
    return user

class Login(BaseModel):
    username: str
    password: str
class StudentIn(BaseModel):
    biometric_id: str = Field(min_length=1, max_length=64)
    first_name: str = Field(min_length=1, max_length=120)
    last_name: str = Field(min_length=1, max_length=120)
    course_id: int = Field(ge=1, le=13)
    email: str | None = None
    active: bool = True
class InspectorIn(BaseModel):
    first_name: str = Field(min_length=1)
    last_name: str = Field(min_length=1)
    username: str = Field(min_length=4)
    email: str | None = None
    type: str = "CURSO"
    course_ids: list[int] = []
    active: bool = True

app = FastAPI(title="DMT Biometría", version="1.0.0")
app.add_middleware(CORSMiddleware, allow_origins=["http://localhost:5173"], allow_credentials=True, allow_methods=["*"], allow_headers=["*"])

rate_buckets: dict[str, deque[float]] = defaultdict(deque)
@app.middleware("http")
async def rate_limit(request: Request, call_next):
    key = f"{request.client.host if request.client else 'unknown'}:{request.url.path}:{request.method}"
    now = time.time(); bucket = rate_buckets[key]
    while bucket and bucket[0] < now - 60: bucket.popleft()
    limit = 10 if request.url.path == "/api/auth/login" else 120
    if len(bucket) >= limit: raise HTTPException(429, "Demasiadas solicitudes. Intente nuevamente en un minuto.")
    bucket.append(now)
    return await call_next(request)

@app.get("/api/health")
def health(): return {"status": "ok", "demo": True}

@app.post("/api/auth/login")
def login(data: Login):
    if data.username == "admin.demo" and data.password == "admin12345":
        return {"token": make_token(data.username, "ADMINISTRATOR"), "user": {"username": data.username, "role": "ADMINISTRATOR"}}
    raise HTTPException(401, "Usuario o contraseña incorrectos")

@app.get("/api/auth/me")
def me(user=Depends(current_admin)): return {"username": user["sub"], "role": user["role"]}

def student_view(s): return {**s, "course": next(c["name"] for c in COURSES if c["id"] == s["course_id"])}
def validate_inspector_slot(data: InspectorIn, editing_id: int | None = None):
    if data.type == "CURSO" and len(data.course_ids) != 1:
        raise HTTPException(400, "El inspector de curso debe tener exactamente un curso")
    if data.type == "GENERAL": data.course_ids = list(range(1, 14))
    for course_id in data.course_ids:
        occupied = [i for i in inspectors if i["id"] != editing_id and i["active"] and i["type"] == data.type and course_id in i["course_ids"]]
        if occupied: raise HTTPException(409, f"El curso {course_id} ya tiene un inspector {data.type.lower()} asignado")
@app.get("/api/courses")
def courses(user=Depends(current_admin)): return COURSES
@app.get("/api/students")
def list_students(search: str = "", course_id: int | None = None, user=Depends(current_admin)):
    q = search.lower().strip(); return [student_view(s) for s in students if (not course_id or s["course_id"] == course_id) and (not q or q in json.dumps(student_view(s)).lower())]
@app.post("/api/students", status_code=201)
def create_student(data: StudentIn, user=Depends(current_admin)):
    global next_student_id
    if any(s["biometric_id"] == data.biometric_id.strip() for s in students): raise HTTPException(409, "El ID biométrico ya existe")
    item = {"id": next_student_id, **data.model_dump(), "biometric_id": data.biometric_id.strip(), "email": data.email.strip() if data.email and data.email.strip() else None}; next_student_id += 1; students.append(item); return student_view(item)
@app.put("/api/students/{student_id}")
def update_student(student_id: int, data: StudentIn, user=Depends(current_admin)):
    item = next((s for s in students if s["id"] == student_id), None)
    if not item: raise HTTPException(404, "Estudiante no encontrado")
    if any(s["id"] != student_id and s["biometric_id"] == data.biometric_id.strip() for s in students): raise HTTPException(409, "El ID biométrico ya existe")
    item.update({**data.model_dump(), "biometric_id": data.biometric_id.strip(), "email": data.email.strip() if data.email and data.email.strip() else None}); return student_view(item)
@app.delete("/api/students/{student_id}")
def delete_student(student_id: int, user=Depends(current_admin)):
    global students; before = len(students); students = [s for s in students if s["id"] != student_id]
    if len(students) == before: raise HTTPException(404, "Estudiante no encontrado")
    return {"deleted": True}

@app.post("/api/students/import")
async def import_students(file: UploadFile = File(...), mode: str = "append", user=Depends(current_admin)):
    global students, next_student_id
    if not file.filename or not file.filename.lower().endswith((".xlsx", ".xlsm")): raise HTTPException(400, "Suba un archivo Excel .xlsx")
    content = await file.read()
    try:
        from openpyxl import load_workbook
        sheet = load_workbook(BytesIO(content), read_only=True, data_only=True).active
        rows = list(sheet.iter_rows(values_only=True)); header_idx = next(i for i, r in enumerate(rows) if r and str(r[0]).strip().upper() == "ID")
        headers = [str(x).strip().lower() if x is not None else "" for x in rows[header_idx]]
        idx = {h: i for i, h in enumerate(headers)}; required = ["id", "nombre", "apellido", "id de departamento", "email"]
        missing = [h for h in required if h not in idx]
        if missing: raise HTTPException(400, f"Faltan columnas: {', '.join(missing)}")
        parsed=[]; errors=[]; seen=set()
        for n, row in enumerate(rows[header_idx+1:], header_idx+2):
            if not any(v is not None for v in row): continue
            bid = str(row[idx["id"]]).strip() if row[idx["id"]] is not None else ""
            try: course_id = int(row[idx["id de departamento"]])
            except Exception: course_id = 0
            if not bid or bid in seen or any(s["biometric_id"] == bid for s in students): errors.append({"row": n, "message": "ID biométrico vacío o repetido"}); continue
            if course_id not in range(1,14): errors.append({"row": n, "message": "ID de departamento debe estar entre 1 y 13"}); continue
            seen.add(bid); parsed.append({"biometric_id": bid, "first_name": str(row[idx["nombre"]] or "").strip(), "last_name": str(row[idx["apellido"]] or "").strip(), "course_id": course_id, "email": str(row[idx["email"]]).strip() if row[idx["email"]] else None, "active": True})
        if errors and mode == "replace": return {"mode": mode, "inserted": 0, "errors": errors, "message": "Corrija el archivo antes de reemplazar"}
        if mode == "replace": students = []
        for item in parsed: item["id"] = next_student_id; next_student_id += 1; students.append(item)
        result={"id": len(load_history)+1, "filename": file.filename, "mode": mode, "inserted": len(parsed), "errors": errors, "at": datetime.now(timezone.utc).isoformat()}; load_history.append(result); return result
    except HTTPException: raise
    except Exception as exc: raise HTTPException(400, f"No se pudo leer el Excel: {exc}") from exc

@app.delete("/api/students")
def clear_students(user=Depends(current_admin)):
    global students; count=len(students); students=[]; return {"deleted": count}
@app.get("/api/students/import-history")
def imports(user=Depends(current_admin)): return list(reversed(load_history))

@app.get("/api/inspectors")
def list_inspectors(user=Depends(current_admin)): return inspectors
@app.post("/api/inspectors", status_code=201)
def create_inspector(data: InspectorIn, user=Depends(current_admin)):
    global next_inspector_id
    if data.type not in ("CURSO", "GENERAL"): raise HTTPException(400, "Tipo de inspector inválido")
    if any(i["username"].lower() == data.username.lower() for i in inspectors): raise HTTPException(409, "El usuario ya existe")
    validate_inspector_slot(data)
    item={"id": next_inspector_id, **data.model_dump()}; next_inspector_id+=1; inspectors.append(item); return item
@app.put("/api/inspectors/{inspector_id}")
def update_inspector(inspector_id: int, data: InspectorIn, user=Depends(current_admin)):
    item=next((i for i in inspectors if i["id"]==inspector_id),None)
    if not item: raise HTTPException(404,"Inspector no encontrado")
    validate_inspector_slot(data, inspector_id)
    item.update(data.model_dump()); return item
@app.delete("/api/inspectors/{inspector_id}")
def delete_inspector(inspector_id:int,user=Depends(current_admin)):
    global inspectors; before=len(inspectors); inspectors=[i for i in inspectors if i["id"]!=inspector_id]
    if len(inspectors)==before: raise HTTPException(404,"Inspector no encontrado")
    return {"deleted":True}
