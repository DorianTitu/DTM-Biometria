package ec.dmt.zkteco.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.core.io.ClassPathResource;
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
            var mail = sender.createMimeMessage(); var helper = new MimeMessageHelper(mail, true, "UTF-8");
            helper.setFrom(from); helper.setTo((testRecipients.isEmpty() ? List.of(student.email()) : testRecipients).toArray(String[]::new));
            helper.setSubject(("ENTRY".equals(notification.type()) ? "Ingreso" : "Salida") + " registrada - " + student.fullName());
            String title = "ENTRY".equals(notification.type()) ? "Ingreso registrado" : "Salida registrada";
            String action = "ENTRY".equals(notification.type())
                    ? "ha ingresado a la institución"
                    : "ha salido de la institución";
            String html = "<div style='margin:0;background:#f4f6f8;padding:24px 10px;font-family:Arial,Helvetica,sans-serif;color:#425466'>" +
                    "<table role='presentation' width='100%' cellspacing='0' cellpadding='0' style='max-width:680px;margin:auto;background:#fff;border:1px solid #dfe6ed;border-radius:14px;overflow:hidden'>" +
                    "<tr><td style='padding:0;background:#fff;text-align:center'><img src='cid:institution-header' alt='Unidad Educativa Técnico Salesiano Don Bosco' width='620' style='display:block;width:100%;max-width:620px;height:auto;margin:0 auto'></td></tr>" +
                    "<tr><td style='padding:34px 34px 28px'>" +
                    "<p style='margin:0 0 22px;font-size:17px;line-height:1.5;color:#425466'>Estimado representante:</p>" +
                    "<p style='margin:0 0 22px;font-size:17px;line-height:1.6;color:#425466'>Le informamos que <strong style='color:#243b53'>" + esc(student.fullName()) + "</strong> " + action + ".</p>" +
                    "<h1 style='font-size:27px;line-height:1.25;margin:0 0 24px;color:#123f6d;font-weight:700'>" + title + "</h1>" +
                    "<table role='presentation' width='100%' cellspacing='0' cellpadding='0' style='background:#eef5fb;border-radius:12px'><tr>" +
                    "<td width='50%' style='padding:20px 22px;vertical-align:top'><div style='font-size:12px;color:#60758b;text-transform:uppercase;letter-spacing:1.2px'>Curso</div><div style='font-size:19px;line-height:1.25;font-weight:700;margin-top:8px;color:#172b4d'>" + esc(student.course()) + "</div></td>" +
                    "<td width='50%' style='padding:20px 22px;vertical-align:top'><div style='font-size:12px;color:#60758b;text-transform:uppercase;letter-spacing:1.2px'>Fecha y hora</div><div style='font-size:19px;line-height:1.35;font-weight:700;margin-top:8px;color:#172b4d'>" + when.replace(" ", "<br>") + "</div></td>" +
                    "</tr></table></td></tr>" +
                    "<tr><td style='padding:20px 24px 8px;background:#fff;border-top:1px solid #edf1f5;text-align:center'><img src='cid:dmt-footer' alt='DMT Sistemas Automatizados de Seguridad' width='620' style='display:block;width:100%;max-width:620px;height:auto;margin:0 auto'></td></tr>" +
                    "<tr><td style='padding:4px 26px 24px;background:#fff;text-align:center;color:#718096;font-size:12px;line-height:1.6'>Mensaje automático de " + esc(institutionName) + ".<br>Por favor, no responda a este correo.</td></tr></table></div>";
            helper.setText(html, true);
            helper.addInline("institution-header", new ClassPathResource("static/email/institution-header.jpg"), "image/jpeg");
            helper.addInline("dmt-footer", new ClassPathResource("static/email/dmt-footer.png"), "image/png");
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
