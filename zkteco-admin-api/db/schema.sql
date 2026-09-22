-- DMT Biometría · PostgreSQL
-- No existen paralelos: cada estudiante pertenece a un único curso.
CREATE EXTENSION IF NOT EXISTS citext;

CREATE TYPE user_role AS ENUM ('ADMINISTRATOR', 'INSPECTOR');
CREATE TYPE inspector_scope AS ENUM ('CURSO', 'GENERAL');
CREATE TYPE biometric_event_type AS ENUM ('ENTRY', 'EXIT', 'UNKNOWN');

CREATE TABLE app_users (
  id BIGSERIAL PRIMARY KEY,
  username CITEXT NOT NULL UNIQUE,
  password_hash TEXT NOT NULL,
  role user_role NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Solo se almacena el hash SHA-256. La clave en claro se muestra una única vez al crearla.
CREATE TABLE api_keys (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(120) NOT NULL,
  key_prefix VARCHAR(24) NOT NULL,
  key_hash CHAR(64) NOT NULL UNIQUE,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_used_at TIMESTAMPTZ
);
CREATE INDEX ix_api_keys_active ON api_keys(active);

CREATE TABLE courses (
  id SMALLINT PRIMARY KEY CHECK (id BETWEEN 1 AND 13),
  name VARCHAR(80) NOT NULL UNIQUE,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE inspectors (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL UNIQUE REFERENCES app_users(id) ON DELETE RESTRICT,
  first_names VARCHAR(120) NOT NULL,
  last_names VARCHAR(120) NOT NULL,
  email CITEXT,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- CURSO requiere course_id; GENERAL cubre todos los cursos y no usa course_id.
CREATE TABLE inspector_assignments (
  id BIGSERIAL PRIMARY KEY,
  inspector_id BIGINT NOT NULL REFERENCES inspectors(id) ON DELETE CASCADE,
  scope inspector_scope NOT NULL,
  course_id SMALLINT REFERENCES courses(id) ON DELETE RESTRICT,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  assigned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK ((scope = 'CURSO' AND course_id IS NOT NULL) OR (scope = 'GENERAL' AND course_id IS NULL))
);
CREATE UNIQUE INDEX uq_active_course_inspector ON inspector_assignments(course_id)
  WHERE active AND scope = 'CURSO';
CREATE UNIQUE INDEX uq_active_general_inspector ON inspector_assignments(scope)
  WHERE active AND scope = 'GENERAL';

CREATE TABLE students (
  id BIGSERIAL PRIMARY KEY,
  biometric_id VARCHAR(64) NOT NULL UNIQUE,
  first_names VARCHAR(160) NOT NULL,
  last_names VARCHAR(160) NOT NULL,
  course_id SMALLINT NOT NULL REFERENCES courses(id) ON DELETE RESTRICT,
  representative_email CITEXT,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_students_course_name ON students(course_id, last_names, first_names);
CREATE INDEX ix_students_active ON students(active);

-- Horario esperado para calcular presente, atrasado y ausente.
CREATE TABLE course_schedules (
  id BIGSERIAL PRIMARY KEY,
  course_id SMALLINT NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
  weekday SMALLINT NOT NULL CHECK (weekday BETWEEN 1 AND 7),
  expected_entry TIME NOT NULL,
  late_after TIME NOT NULL,
  entry_start TIME NOT NULL DEFAULT '06:00',
  entry_end TIME NOT NULL DEFAULT '07:30',
  exit_start TIME NOT NULL DEFAULT '13:00',
  exit_end TIME NOT NULL DEFAULT '15:00',
  active BOOLEAN NOT NULL DEFAULT TRUE,
  UNIQUE(course_id, weekday)
);

-- Fuente inmutable de la información recibida desde ZKTeco.
CREATE TABLE attendance_events (
  id BIGSERIAL PRIMARY KEY,
  student_id BIGINT REFERENCES students(id) ON DELETE SET NULL,
  biometric_id VARCHAR(64) NOT NULL,
  event_type biometric_event_type NOT NULL DEFAULT 'UNKNOWN',
  occurred_at TIMESTAMPTZ NOT NULL,
  device_id VARCHAR(120),
  source_event_id VARCHAR(160),
  raw_payload JSONB,
  received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(device_id, source_event_id)
);
CREATE INDEX ix_attendance_events_student_time ON attendance_events(student_id, occurred_at DESC);
CREATE INDEX ix_attendance_events_biometric_time ON attendance_events(biometric_id, occurred_at DESC);
CREATE INDEX ix_attendance_events_time ON attendance_events(occurred_at DESC);

-- Registro diario consultable para el dashboard. Se genera/refresca desde los eventos.
CREATE TABLE daily_attendance (
  attendance_date DATE NOT NULL,
  student_id BIGINT NOT NULL REFERENCES students(id) ON DELETE CASCADE,
  first_entry_at TIMESTAMPTZ,
  last_exit_at TIMESTAMPTZ,
  status VARCHAR(20) NOT NULL CHECK (status IN ('PRESENT', 'LATE', 'ABSENT', 'NO_EXIT', 'UNKNOWN')),
  calculated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY(attendance_date, student_id)
);
CREATE INDEX ix_daily_attendance_date_status ON daily_attendance(attendance_date, status);
CREATE INDEX ix_daily_attendance_student_date ON daily_attendance(student_id, attendance_date DESC);

-- Una notificación por estudiante, fecha y tipo. El listener de correo consumirá esta cola.
CREATE TABLE attendance_notifications (
  id BIGSERIAL PRIMARY KEY,
  attendance_date DATE NOT NULL,
  student_id BIGINT NOT NULL REFERENCES students(id) ON DELETE CASCADE,
  event_type biometric_event_type NOT NULL CHECK (event_type IN ('ENTRY','EXIT')),
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','PROCESSING','SENT','FAILED')),
  attempts INTEGER NOT NULL DEFAULT 0,
  sent_at TIMESTAMPTZ,
  last_error TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(attendance_date, student_id, event_type)
);
CREATE INDEX ix_attendance_notifications_pending ON attendance_notifications(status, created_at)
  WHERE status = 'PENDING';

CREATE TABLE import_runs (
  id BIGSERIAL PRIMARY KEY,
  filename TEXT NOT NULL,
  mode VARCHAR(20) NOT NULL CHECK (mode IN ('append', 'replace')),
  inserted_count INTEGER NOT NULL DEFAULT 0,
  error_count INTEGER NOT NULL DEFAULT 0,
  errors JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO courses (id, name) VALUES
(1,'1ro BASICA'),(2,'2do BASICA'),(3,'3ro BASICA'),(4,'4to BASICA'),
(5,'5to BASICA'),(6,'6to BASICA'),(7,'7mo BASICA'),(8,'8vo BASICA'),
(9,'9no BASICA'),(10,'10mo BASICA'),(11,'1ro BACH'),(12,'2do BACH'),(13,'3ro BACH')
ON CONFLICT (id) DO NOTHING;
