package ec.dmt.zkteco.service;

import ec.dmt.zkteco.domain.AuthenticationEvent;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

class AuthEventConsumerTest {
    private AuthenticationEvent event(long id) {
        return new AuthenticationEvent(id, "TEST", String.valueOf(id),
                OffsetDateTime.now(), 1, 255);
    }

    @Test
    void flushesAutomaticallyWhenBatchReaches500() {
        BatchSink sink = mock(BatchSink.class); AuthEventConsumer consumer = new AuthEventConsumer(sink);
        for (int i = 1; i <= 500; i++) consumer.consume(event(i)); verify(sink).send(argThat(events -> events.size()==500));
    }

    @Test
    void scheduledFlushCanProcessPartialBatch() {
        BatchSink sink = mock(BatchSink.class); AuthEventConsumer consumer = new AuthEventConsumer(sink);
        consumer.consume(event(1));
        consumer.scheduledFlush();
        consumer.flush(); verify(sink).send(argThat(events -> events.size()==1));
    }
}
