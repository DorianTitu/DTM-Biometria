from __future__ import annotations
import hashlib, os
from pathlib import Path
import psycopg
from psycopg.errors import DuplicateObject, DuplicateTable
from psycopg.rows import dict_row

DATABASE_URL = os.getenv('DATABASE_URL', 'postgresql://postgres:postgres@localhost:5432/dmt_biometria')
SCHEMA = Path(__file__).with_name('db').joinpath('schema.sql').read_text()

def connect():
    return psycopg.connect(DATABASE_URL, row_factory=dict_row)

def password_hash(value: str) -> str:
    return hashlib.sha256(value.encode()).hexdigest()

def init_db():
    import re
    with connect() as conn:
        exists = conn.execute("SELECT to_regclass('public.app_users') AS table_name").fetchone()['table_name']
        if not exists:
            clean = re.sub(r"--[^\n]*", "", SCHEMA)
            for statement in clean.split(";"):
                if statement.strip(): conn.execute(statement)
        # Migraciones seguras para instalaciones que ya existían antes de nuevas tablas.
        conn.execute("""CREATE TABLE IF NOT EXISTS attendance_notifications (
          id BIGSERIAL PRIMARY KEY, attendance_date DATE NOT NULL,
          student_id BIGINT NOT NULL REFERENCES students(id) ON DELETE CASCADE,
          event_type biometric_event_type NOT NULL CHECK (event_type IN ('ENTRY','EXIT')),
          status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','SENT','FAILED')),
          attempts INTEGER NOT NULL DEFAULT 0, sent_at TIMESTAMPTZ, last_error TEXT,
          created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
          UNIQUE(attendance_date, student_id, event_type))""")
        conn.execute("""CREATE INDEX IF NOT EXISTS ix_attendance_notifications_pending
          ON attendance_notifications(status, created_at) WHERE status = 'PENDING'""")
        conn.execute("ALTER TABLE attendance_notifications DROP CONSTRAINT IF EXISTS attendance_notifications_status_check")
        conn.execute("""ALTER TABLE attendance_notifications ADD CONSTRAINT attendance_notifications_status_check
          CHECK (status IN ('PENDING','PROCESSING','SENT','FAILED'))""")
        conn.execute("""CREATE TABLE IF NOT EXISTS api_keys (
          id BIGSERIAL PRIMARY KEY,
          name VARCHAR(120) NOT NULL,
          key_prefix VARCHAR(24) NOT NULL,
          key_hash CHAR(64) NOT NULL UNIQUE,
          active BOOLEAN NOT NULL DEFAULT TRUE,
          created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
          last_used_at TIMESTAMPTZ
        )""")
        conn.execute("CREATE INDEX IF NOT EXISTS ix_api_keys_active ON api_keys(active)")
        conn.execute("ALTER TABLE course_schedules ADD COLUMN IF NOT EXISTS entry_start TIME NOT NULL DEFAULT '06:00'")
        conn.execute("ALTER TABLE course_schedules ADD COLUMN IF NOT EXISTS entry_end TIME NOT NULL DEFAULT '07:30'")
        conn.execute("ALTER TABLE course_schedules ADD COLUMN IF NOT EXISTS exit_start TIME NOT NULL DEFAULT '13:00'")
        conn.execute("ALTER TABLE course_schedules ADD COLUMN IF NOT EXISTS exit_end TIME NOT NULL DEFAULT '15:00'")
        conn.execute("INSERT INTO app_users(username,password_hash,role) VALUES (%s,%s,'ADMINISTRATOR') ON CONFLICT(username) DO NOTHING", ('admin.demo', password_hash('admin12345')))
        conn.execute("INSERT INTO students(biometric_id,first_names,last_names,course_id,representative_email) VALUES ('1001','JOAN SEBASTIAN','ARMIJOS TORRES',1,'dyaniliz@hotmail.com'),('1002','MARIA JOSE','CASTRO VEGA',8,NULL),('1003','DANIEL','MOLINA ROJAS',11,'familia@example.com') ON CONFLICT(biometric_id) DO NOTHING")
        conn.commit()
