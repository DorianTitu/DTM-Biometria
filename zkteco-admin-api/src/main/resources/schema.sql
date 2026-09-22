CREATE TABLE IF NOT EXISTS app_user (
 id BIGSERIAL PRIMARY KEY,
 username VARCHAR(80) UNIQUE NOT NULL,
 password_hash VARCHAR(100) NOT NULL,
 role VARCHAR(30) NOT NULL CHECK (role IN ('ADMINISTRATOR','INSPECTOR')),
 active BOOLEAN NOT NULL DEFAULT TRUE,
 failed_attempts INTEGER NOT NULL DEFAULT 0,
 locked_until TIMESTAMPTZ,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_app_user_username ON app_user (username);

CREATE TABLE IF NOT EXISTS course (id BIGSERIAL PRIMARY KEY, name VARCHAR(120) NOT NULL UNIQUE, active BOOLEAN NOT NULL DEFAULT TRUE);
ALTER TABLE course ADD COLUMN IF NOT EXISTS course_order INTEGER NOT NULL DEFAULT 0;
ALTER TABLE course ADD COLUMN IF NOT EXISTS level VARCHAR(30) NOT NULL DEFAULT 'EGB';
CREATE TABLE IF NOT EXISTS parallel (id BIGSERIAL PRIMARY KEY, course_id BIGINT NOT NULL REFERENCES course(id), name VARCHAR(20) NOT NULL, active BOOLEAN NOT NULL DEFAULT TRUE, UNIQUE(course_id, name));
CREATE TABLE IF NOT EXISTS student (id BIGSERIAL PRIMARY KEY, biometric_user_id VARCHAR(64) NOT NULL UNIQUE, first_name VARCHAR(100) NOT NULL, last_name VARCHAR(100) NOT NULL, parallel_id BIGINT NOT NULL REFERENCES parallel(id), active BOOLEAN NOT NULL DEFAULT TRUE);
CREATE TABLE IF NOT EXISTS guardian (id BIGSERIAL PRIMARY KEY, student_id BIGINT NOT NULL REFERENCES student(id), first_name VARCHAR(100) NOT NULL, last_name VARCHAR(100) NOT NULL, email VARCHAR(255) NOT NULL, active BOOLEAN NOT NULL DEFAULT TRUE, UNIQUE(student_id, email));
CREATE TABLE IF NOT EXISTS academic_year (id BIGSERIAL PRIMARY KEY, name VARCHAR(30) NOT NULL UNIQUE, status VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','CLOSED')));
CREATE TABLE IF NOT EXISTS enrollment (id BIGSERIAL PRIMARY KEY, student_id BIGINT NOT NULL REFERENCES student(id), academic_year_id BIGINT NOT NULL REFERENCES academic_year(id), parallel_id BIGINT NOT NULL REFERENCES parallel(id), status VARCHAR(20) NOT NULL DEFAULT 'ENROLLED', UNIQUE(student_id, academic_year_id));
CREATE TABLE IF NOT EXISTS inspector_course (id BIGSERIAL PRIMARY KEY, user_id BIGINT NOT NULL REFERENCES app_user(id), course_id BIGINT NOT NULL REFERENCES course(id), created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE(user_id,course_id));
CREATE INDEX IF NOT EXISTS idx_enrollment_year ON enrollment(academic_year_id);
CREATE INDEX IF NOT EXISTS idx_inspector_course_user ON inspector_course(user_id);
CREATE TABLE IF NOT EXISTS attendance_event (id BIGSERIAL PRIMARY KEY, student_id BIGINT REFERENCES student(id), biometric_user_id VARCHAR(64) NOT NULL, device_serial VARCHAR(100) NOT NULL, event_time TIMESTAMPTZ NOT NULL, received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE(biometric_user_id,device_serial,event_time));
CREATE INDEX IF NOT EXISTS idx_attendance_event_time ON attendance_event(event_time);

-- Idempotent bootstrap of the current academic structure and legacy data.
UPDATE course SET course_order = CASE lower(name)
  WHEN 'primero' THEN 1 WHEN '1ro' THEN 1 WHEN 'segundo' THEN 2 WHEN '2do' THEN 2 WHEN 'tercero' THEN 3 WHEN '3ro' THEN 3
  WHEN 'cuarto' THEN 4 WHEN 'quinto' THEN 5 WHEN 'sexto' THEN 6
  WHEN 'séptimo' THEN 7 WHEN 'septimo' THEN 7 WHEN 'octavo' THEN 8
  WHEN 'noveno' THEN 9 WHEN 'décimo' THEN 10 WHEN 'decimo' THEN 10
  WHEN 'primero de bachillerato' THEN 11 WHEN 'segundo de bachillerato' THEN 12
  WHEN 'tercero de bachillerato' THEN 13 ELSE course_order END,
  level = CASE WHEN lower(name) LIKE '%bachillerato%' THEN 'BACHILLERATO' ELSE 'EGB' END
WHERE course_order = 0;
INSERT INTO academic_year(name,status) VALUES ('2026-2027','OPEN') ON CONFLICT (name) DO NOTHING;
INSERT INTO enrollment(student_id,academic_year_id,parallel_id,status)
SELECT s.id,y.id,s.parallel_id,'MIGRATED'
FROM student s CROSS JOIN academic_year y
WHERE y.name='2026-2027' AND NOT EXISTS
 (SELECT 1 FROM enrollment e WHERE e.student_id=s.id AND e.academic_year_id=y.id);

-- Development bootstrap accounts. Change their passwords or disable them before exposing the API.
INSERT INTO app_user (username, password_hash, role)
VALUES
  ('admin.demo', '$2y$12$CQizo2BU8T3am/jiq/rOmeABuXpKW2HMYiguWxy966sTLTvUJl4Yi', 'ADMINISTRATOR'),
  ('inspector.demo', '$2y$12$CQizo2BU8T3am/jiq/rOmeABuXpKW2HMYiguWxy966sTLTvUJl4Yi', 'INSPECTOR')
ON CONFLICT (username) DO NOTHING;
