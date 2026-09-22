CREATE TABLE course (id BIGSERIAL PRIMARY KEY, name VARCHAR(120) NOT NULL UNIQUE, active BOOLEAN NOT NULL DEFAULT TRUE);
CREATE TABLE parallel (id BIGSERIAL PRIMARY KEY, course_id BIGINT NOT NULL REFERENCES course(id), name VARCHAR(20) NOT NULL, active BOOLEAN NOT NULL DEFAULT TRUE, UNIQUE(course_id,name));
CREATE TABLE student (id BIGSERIAL PRIMARY KEY, biometric_user_id VARCHAR(64) NOT NULL UNIQUE, first_name VARCHAR(100) NOT NULL, last_name VARCHAR(100) NOT NULL, parallel_id BIGINT NOT NULL REFERENCES parallel(id), active BOOLEAN NOT NULL DEFAULT TRUE);
CREATE TABLE guardian (id BIGSERIAL PRIMARY KEY, student_id BIGINT NOT NULL REFERENCES student(id), first_name VARCHAR(100) NOT NULL, last_name VARCHAR(100) NOT NULL, email VARCHAR(255) NOT NULL, active BOOLEAN NOT NULL DEFAULT TRUE, UNIQUE(student_id,email));
CREATE TABLE attendance_event (id BIGSERIAL PRIMARY KEY, student_id BIGINT REFERENCES student(id), biometric_user_id VARCHAR(64) NOT NULL, device_serial VARCHAR(100) NOT NULL, event_time TIMESTAMPTZ NOT NULL, received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE(biometric_user_id,device_serial,event_time));
CREATE TABLE notification (id BIGSERIAL PRIMARY KEY, attendance_event_id BIGINT NOT NULL REFERENCES attendance_event(id), guardian_id BIGINT NOT NULL REFERENCES guardian(id), status VARCHAR(20) NOT NULL DEFAULT 'PENDING', attempts INT NOT NULL DEFAULT 0, sent_at TIMESTAMPTZ, error_message TEXT, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE(attendance_event_id,guardian_id));
CREATE INDEX idx_student_biometric ON student(biometric_user_id);
CREATE INDEX idx_attendance_event_time ON attendance_event(event_time);
CREATE INDEX idx_notification_status ON notification(status);
