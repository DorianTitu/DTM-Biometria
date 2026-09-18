package ec.dmt.zkteco.service;

import ec.dmt.zkteco.config.RabbitConfig;
import ec.dmt.zkteco.domain.AuthenticationEvent;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Service
public class AttendanceEventConsumer {
    private final RestClient backend;
    private final String listenerKey;

    public AttendanceEventConsumer(@Value("${DMT_BACKEND_URL:http://localhost:8000}") String backendUrl,
                                  @Value("${LISTENER_API_KEY:change-this-listener-key}") String listenerKey) {
        this.backend = RestClient.builder().baseUrl(backendUrl)
                .requestFactory(new HttpComponentsClientHttpRequestFactory()).build();
        this.listenerKey = listenerKey;
    }

    @RabbitListener(queues = RabbitConfig.QUEUE)
    public void consume(AuthenticationEvent event) {
        String sourceEventId = event.deviceSerial() + ":" + event.authenticatedAt() + ":" + event.userId() + ":" + event.verifyType() + ":" + event.status();
        backend.post().uri("/api/attendance/events").contentType(MediaType.APPLICATION_JSON)
                .header("X-Listener-Key", listenerKey)
                .body(Map.of("biometric_id", event.userId(), "occurred_at", event.authenticatedAt().toString(),
                        "event_type", event.status() == 1 ? "EXIT" : "ENTRY", "device_id", event.deviceSerial(),
                        "source_event_id", sourceEventId, "verify_type", event.verifyType(), "device_status", event.status()))
                .retrieve().toBodilessEntity();
    }
}
