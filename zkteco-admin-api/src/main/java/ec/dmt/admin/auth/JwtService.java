package ec.dmt.admin.auth;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
@Service public class JwtService {
    private final SecretKey key; private final int ttlMinutes;
    public JwtService(@Value("${app.jwt.secret}") String secret, @Value("${app.jwt.ttl-minutes}") int ttlMinutes) { if (secret.getBytes(StandardCharsets.UTF_8).length < 32) throw new IllegalStateException("JWT_SECRET debe tener al menos 32 bytes"); this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)); this.ttlMinutes = ttlMinutes; }
    public Token issue(String username, String role) { Instant expiry = Instant.now().plus(ttlMinutes, ChronoUnit.MINUTES); String value = Jwts.builder().subject(username).claim("role", role).issuedAt(new Date()).expiration(Date.from(expiry)).signWith(key).compact(); return new Token(value, expiry.getEpochSecond()); }
    public Claims parse(String token) { return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload(); }
    public record Token(String value, long expiresAt) { }
}
