-- Datos mínimos de prueba para DTM-Biometrico-ZKTeco
-- Ejecutar después de crear el esquema:
-- psql -h localhost -p 5433 -U biometric -d biometric -f scripts/seed-test.sql

BEGIN;

INSERT INTO academic_year (name, active)
VALUES ('2026-2027', TRUE)
ON CONFLICT (name) DO UPDATE SET active = TRUE;

INSERT INTO course (name, course_order, level, active)
VALUES
  ('Primero de Básica', 1, 'EGB', TRUE),
  ('Primero de Bachillerato', 11, 'BACHILLERATO', TRUE)
ON CONFLICT (name) DO UPDATE SET
  course_order = EXCLUDED.course_order,
  level = EXCLUDED.level,
  active = TRUE;

INSERT INTO parallel (name, course_id, active)
SELECT 'A', id, TRUE
FROM course
WHERE name IN ('Primero de Básica', 'Primero de Bachillerato')
ON CONFLICT (course_id, name) DO UPDATE SET active = TRUE;

INSERT INTO student (biometric_user_id, first_name, last_name, parallel_id, active)
SELECT '1', 'Ana', 'Pérez Demo', p.id, TRUE
FROM parallel p JOIN course c ON c.id = p.course_id
WHERE c.name = 'Primero de Básica' AND p.name = 'A'
ON CONFLICT (biometric_user_id) DO UPDATE SET
  first_name = EXCLUDED.first_name,
  last_name = EXCLUDED.last_name,
  parallel_id = EXCLUDED.parallel_id,
  active = TRUE;

INSERT INTO student (biometric_user_id, first_name, last_name, parallel_id, active)
SELECT '2', 'Carlos', 'Gómez Demo', p.id, TRUE
FROM parallel p JOIN course c ON c.id = p.course_id
WHERE c.name = 'Primero de Bachillerato' AND p.name = 'A'
ON CONFLICT (biometric_user_id) DO UPDATE SET
  first_name = EXCLUDED.first_name,
  last_name = EXCLUDED.last_name,
  parallel_id = EXCLUDED.parallel_id,
  active = TRUE;

INSERT INTO guardian (student_id, first_name, last_name, email, active)
SELECT s.id, 'Dorian', 'Tituana', 'dorian.tituana@epn.edu.ec', TRUE
FROM student s
WHERE s.biometric_user_id IN ('1', '2')
  AND NOT EXISTS (
    SELECT 1 FROM guardian g
    WHERE g.student_id = s.id AND g.email = 'dorian.tituana@epn.edu.ec'
  );

INSERT INTO enrollment (student_id, academic_year_id, parallel_id, status)
SELECT s.id, ay.id, s.parallel_id, 'ENROLLED'
FROM student s CROSS JOIN academic_year ay
WHERE s.biometric_user_id IN ('1', '2') AND ay.name = '2026-2027'
ON CONFLICT (student_id, academic_year_id) DO UPDATE SET
  parallel_id = EXCLUDED.parallel_id,
  status = 'ENROLLED';

-- Una marcación por estudiante en la fecha actual; evita duplicados al repetir el script.
INSERT INTO attendance_event (student_id, biometric_user_id, device_serial, event_time)
SELECT s.id, s.biometric_user_id, 'TEST-DEVICE', CURRENT_TIMESTAMP
FROM student s
WHERE s.biometric_user_id IN ('1', '2')
  AND NOT EXISTS (
    SELECT 1 FROM attendance_event a
    WHERE a.biometric_user_id = s.biometric_user_id
      AND a.device_serial = 'TEST-DEVICE'
      AND a.event_time::date = CURRENT_DATE
  );

COMMIT;

-- Verificación
SELECT s.biometric_user_id, s.first_name, s.last_name,
       c.name AS course, p.name AS parallel, g.email,
       ay.name AS academic_year, e.status
FROM student s
JOIN parallel p ON p.id = s.parallel_id
JOIN course c ON c.id = p.course_id
LEFT JOIN guardian g ON g.student_id = s.id
LEFT JOIN enrollment e ON e.student_id = s.id
LEFT JOIN academic_year ay ON ay.id = e.academic_year_id
WHERE s.biometric_user_id IN ('1', '2')
ORDER BY s.biometric_user_id;
