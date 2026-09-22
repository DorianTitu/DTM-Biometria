package ec.dmt.zkteco.service;
import ec.dmt.zkteco.domain.AuthenticationEvent;
import org.springframework.scheduling.annotation.Scheduled;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Legacy helper retained for tests; the live worker polls the persistent outbox. */
public class AuthEventConsumer {
    private static final int MAX_BATCH = 500;
    private final ConcurrentLinkedQueue<AuthenticationEvent> pending = new ConcurrentLinkedQueue<>();
    private final BatchSink sink;
    public AuthEventConsumer(BatchSink sink) { this.sink = sink; }
    public void consume(AuthenticationEvent event) { pending.add(event); if (pending.size() >= MAX_BATCH) flush(); }
    @Scheduled(fixedDelayString = "${zkteco.batch.flush-ms:10000}")
    public void scheduledFlush() { flush(); }
    public synchronized void flush() {
        List<AuthenticationEvent> batch = new ArrayList<>(MAX_BATCH);
        while (batch.size() < MAX_BATCH) {
            AuthenticationEvent event = pending.poll();
            if (event == null) break;
            batch.add(event);
        }
        if (batch.isEmpty()) return;
        sink.send(batch);
        if (!pending.isEmpty()) flush();
    }
}
