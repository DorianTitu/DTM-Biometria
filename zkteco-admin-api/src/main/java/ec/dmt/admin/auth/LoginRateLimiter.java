package ec.dmt.admin.auth;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
@Component public class LoginRateLimiter {
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>(); private final int maxAttempts; private final long windowSeconds;
    public LoginRateLimiter(@Value("${app.auth.rate-limit.attempts}") int maxAttempts, @Value("${app.auth.rate-limit.window-seconds}") long windowSeconds) { this.maxAttempts = maxAttempts; this.windowSeconds = windowSeconds; }
    public boolean allow(String key) { Instant now = Instant.now(); Window result = windows.compute(key, (ignored, old) -> old == null || now.isAfter(old.startedAt.plusSeconds(windowSeconds)) ? new Window(now, 1) : new Window(old.startedAt, old.count + 1)); return result.count <= maxAttempts; }
    private record Window(Instant startedAt, int count) { }
}
