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
        conn.execute("""CREATE TABLE IF NOT EXISTS course_parallels (
          id BIGSERIAL PRIMARY KEY,
          course_id SMALLINT NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
          code VARCHAR(16) NOT NULL,
          active BOOLEAN NOT NULL DEFAULT TRUE,
          created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
          UNIQUE (course_id, code)
        )""")
        conn.execute("CREATE INDEX IF NOT EXISTS ix_course_parallels_course ON course_parallels(course_id, code)")
        # La primera versión restringía cada curso a un único inspector activo.
        # Se conserva la asignación actual y se elimina únicamente esa restricción.
        conn.execute("DROP INDEX IF EXISTS uq_active_course_inspector")
        conn.execute("""CREATE INDEX IF NOT EXISTS ix_active_course_inspector
          ON inspector_assignments(course_id) WHERE active AND scope = 'CURSO'""")
        conn.execute("ALTER TABLE students ADD COLUMN IF NOT EXISTS parallel_id BIGINT REFERENCES course_parallels(id) ON DELETE RESTRICT")
        conn.execute("CREATE INDEX IF NOT EXISTS ix_students_parallel_name ON students(parallel_id, last_names, first_names)")
        # La asistencia persiste únicamente sus horas; el estado se calcula al consultar el horario del curso.
        conn.execute("ALTER TABLE daily_attendance DROP COLUMN IF EXISTS status")
        conn.execute("CREATE INDEX IF NOT EXISTS ix_daily_attendance_date ON daily_attendance(attendance_date)")
        conn.execute("ALTER TABLE course_schedules ADD COLUMN IF NOT EXISTS entry_start TIME NOT NULL DEFAULT '06:00'")
        conn.execute("ALTER TABLE course_schedules ADD COLUMN IF NOT EXISTS entry_end TIME NOT NULL DEFAULT '07:30'")
        conn.execute("ALTER TABLE course_schedules ADD COLUMN IF NOT EXISTS exit_start TIME NOT NULL DEFAULT '13:00'")
        conn.execute("ALTER TABLE course_schedules ADD COLUMN IF NOT EXISTS exit_end TIME NOT NULL DEFAULT '15:00'")
        # Cada curso inicia con los paralelos operativos habituales; la importación puede añadir otros.
        conn.execute("""INSERT INTO course_parallels(course_id,code)
          SELECT c.id, p.code FROM courses c CROSS JOIN (VALUES ('A'),('B'),('C'),('D'),('E'),('F')) AS p(code)
          ON CONFLICT(course_id,code) DO UPDATE SET active=true""")
        conn.execute("INSERT INTO app_users(username,password_hash,role) VALUES (%s,%s,'ADMINISTRATOR') ON CONFLICT(username) DO NOTHING", ('admin.demo', password_hash('admin12345')))
        conn.execute("""INSERT INTO course_parallels(course_id,code) VALUES (1,'A'),(8,'A'),(11,'A')
          ON CONFLICT(course_id,code) DO NOTHING""")
        conn.execute("""INSERT INTO students(biometric_id,first_names,last_names,course_id,parallel_id,representative_email)
          VALUES ('1001','JOAN SEBASTIAN','ARMIJOS TORRES',1,(SELECT id FROM course_parallels WHERE course_id=1 AND code='A'),'dyaniliz@hotmail.com'),
                 ('1002','MARIA JOSE','CASTRO VEGA',8,(SELECT id FROM course_parallels WHERE course_id=8 AND code='A'),NULL),
                 ('1003','DANIEL','MOLINA ROJAS',11,(SELECT id FROM course_parallels WHERE course_id=11 AND code='A'),'familia@example.com')
          ON CONFLICT(biometric_id) DO NOTHING""")
        # Solo los registros de demostración reciben una asignación automática. Los datos reales no se alteran.
        conn.execute("""UPDATE students s SET parallel_id=p.id FROM course_parallels p
          WHERE s.parallel_id IS NULL AND p.code='A' AND p.course_id=s.course_id
            AND s.biometric_id IN ('1001','1002','1003')""")
        conn.commit()
