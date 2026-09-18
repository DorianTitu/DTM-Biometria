package ec.dmt.zkteco.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Arrays;

@Service
public class EmailNotificationService {
    private final JdbcTemplate jdbc;
    private final JavaMailSender sender;
    private final boolean enabled;
    private final String from;
    private final String institutionName;
    private final List<String> testRecipients;

    public EmailNotificationService(JdbcTemplate jdbc, JavaMailSender sender,
                                    @Value("${MAIL_ENABLED:false}") boolean enabled,
                                    @Value("${SMTP_FROM:}") String from,
                                    @Value("${INSTITUTION_NAME:DMT Biometría}") String institutionName,
                                    @Value("${MAIL_TEST_RECIPIENTS:}") String testRecipients) {
        this.jdbc = jdbc; this.sender = sender; this.enabled = enabled; this.from = from; this.institutionName = institutionName;
        this.testRecipients = Arrays.stream(testRecipients.split(",")).map(String::trim).filter(value -> !value.isBlank()).toList();
    }

    @Scheduled(fixedDelayString = "${dmt.notifications.poll-ms:10000}")
    public void processPendingNotifications() {
        if (!enabled || from.isBlank()) return;
        List<Notification> pending = jdbc.query("""
            WITH claimed AS (
              SELECT id FROM attendance_notifications WHERE status='PENDING'
              ORDER BY created_at FOR UPDATE SKIP LOCKED LIMIT 20
            )
            UPDATE attendance_notifications n SET status='PROCESSING',attempts=attempts+1
            FROM claimed WHERE n.id=claimed.id
            RETURNING n.id,n.event_type,n.attendance_date,n.student_id
            """, (rs, row) -> new Notification(rs.getLong("id"), rs.getString("event_type"), rs.getObject("attendance_date", java.time.LocalDate.class), rs.getLong("student_id")));
        for (Notification notification : pending) send(notification);
    }

    private void send(Notification notification) {
        try {
            Student student = jdbc.query("""
                SELECT s.first_names,s.last_names,s.representative_email,c.name,d.first_entry_at,d.last_exit_at
                FROM students s JOIN courses c ON c.id=s.course_id
                JOIN daily_attendance d ON d.student_id=s.id AND d.attendance_date=?
                WHERE s.id=? AND s.active
                """, rs -> rs.next() ? new Student(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getObject(5, java.time.OffsetDateTime.class),rs.getObject(6, java.time.OffsetDateTime.class)) : null,
                    notification.date(), notification.studentId());
            if (student == null || (testRecipients.isEmpty() && (student.email() == null || student.email().isBlank()))) {
                jdbc.update("UPDATE attendance_notifications SET status='SENT',sent_at=now(),last_error='Sin correo de representante' WHERE id=?", notification.id());
                return;
            }
            var eventTime = "ENTRY".equals(notification.type()) ? student.entry() : student.exit();
            String when = eventTime == null ? notification.date().toString() : eventTime.atZoneSameInstant(ZoneId.of("America/Guayaquil")).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
            String action = "ENTRY".equals(notification.type()) ? "ingresó" : "registró su salida";
            var mail = sender.createMimeMessage(); var helper = new MimeMessageHelper(mail, false, "UTF-8");
            helper.setFrom(from); helper.setTo((testRecipients.isEmpty() ? List.of(student.email()) : testRecipients).toArray(String[]::new));
            helper.setSubject(("ENTRY".equals(notification.type()) ? "Ingreso" : "Salida") + " registrada - " + student.fullName());
            helper.setText("<div style='font-family:Arial,sans-serif;color:#1d2c33'><h2>" + esc(institutionName) + "</h2>" +
                    "<p>Estimado representante:</p><p>" + esc(student.fullName()) + " " + action + " a la institución.</p>" +
                    "<p><b>Curso:</b> " + esc(student.course()) + "<br/><b>Fecha y hora:</b> " + when + "</p>" +
                    "<p style='color:#64748b;font-size:12px'>Mensaje automático, no responda este correo.</p></div>", true);
            sender.send(mail);
            jdbc.update("UPDATE attendance_notifications SET status='SENT',sent_at=now(),last_error=NULL WHERE id=?", notification.id());
        } catch (Exception ex) {
            jdbc.update("UPDATE attendance_notifications SET status='FAILED',last_error=? WHERE id=?", ex.getMessage(), notification.id());
        }
    }

    private static String esc(String value) { return value == null ? "" : value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;"); }
    private record Notification(long id, String type, java.time.LocalDate date, long studentId) { }
    private record Student(String first, String last, String email, String course, java.time.OffsetDateTime entry, java.time.OffsetDateTime exit) { String fullName(){ return last + " " + first; } }
}
