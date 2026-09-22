package ec.dmt.zkteco.service;

import ec.dmt.zkteco.domain.AuthenticationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class AuthenticationLogService {
    private static final Logger log = LoggerFactory.getLogger(AuthenticationLogService.class);
    private final AtomicLong sequence = new AtomicLong();
    private final AuthEventPublisher publisher;
    public AuthenticationLogService(AuthEventPublisher publisher) { this.publisher = publisher; }

    public AuthenticationEvent record(String deviceSerial, String userId, String dateTime,
                                      int verifyType, int status) {
        AuthenticationEvent event = new AuthenticationEvent(
                sequence.incrementAndGet(), deviceSerial, userId, parseDateTime(dateTime), verifyType, status);
        log.info("Hola usuario id: {}", event.userId());
        publisher.publish(event);
        log.info("[AUTH_OK] eventId={} userId={} device={} authenticatedAt={} verifyType={} status={}",
                event.eventId(), event.userId(), event.deviceSerial(), event.authenticatedAt(),
                event.verifyType(), event.status());
        return event;
    }

    private OffsetDateTime parseDateTime(String value) {
        try {
            return LocalDateTime.parse(value.replace(' ', 'T')).atOffset(ZoneOffset.of("-05:00"));
        } catch (RuntimeException ex) {
            return OffsetDateTime.now(ZoneOffset.of("-05:00"));
        }
    }
}
