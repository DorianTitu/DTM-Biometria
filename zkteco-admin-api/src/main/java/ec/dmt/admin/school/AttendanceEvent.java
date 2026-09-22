package ec.dmt.admin.school;
import jakarta.persistence.*; import java.time.Instant;
@Entity @Table(name="attendance_event") public class AttendanceEvent { @Id private Long id; @Column(name="biometric_user_id") private String biometricUserId; @Column(name="event_time") private Instant eventTime; public String getBiometricUserId(){return biometricUserId;} public Instant getEventTime(){return eventTime;} }
