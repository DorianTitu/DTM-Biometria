package ec.dmt.admin.auth;
import ec.dmt.admin.user.AppUser;
import ec.dmt.admin.user.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
@Service public class AuthService {
    private final AppUserRepository users; private final PasswordEncoder passwordEncoder; private final JwtService jwtService; private final int maxFailedAttempts; private final int lockMinutes;
    public AuthService(AppUserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService, @Value("${app.auth.max-failed-attempts}") int maxFailedAttempts, @Value("${app.auth.lock-minutes}") int lockMinutes) { this.users = users; this.passwordEncoder = passwordEncoder; this.jwtService = jwtService; this.maxFailedAttempts = maxFailedAttempts; this.lockMinutes = lockMinutes; }
    @Transactional public AuthController.LoginResponse login(String username, String password) {
        AppUser user = users.findByUsername(username).orElseThrow(this::invalidCredentials); Instant now = Instant.now();
        if (!user.isActive() || (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now))) throw invalidCredentials();
        if (!passwordEncoder.matches(password, user.getPasswordHash())) { int attempts = user.getFailedAttempts() + 1; user.setFailedAttempts(attempts); if (attempts >= maxFailedAttempts) { user.setLockedUntil(now.plus(lockMinutes, ChronoUnit.MINUTES)); user.setFailedAttempts(0); } users.save(user); throw invalidCredentials(); }
        user.setFailedAttempts(0); user.setLockedUntil(null); users.save(user); JwtService.Token token = jwtService.issue(user.getUsername(), user.getRole()); return new AuthController.LoginResponse(token.value(), user.getRole(), user.getUsername(), token.expiresAt());
    }
    private ResponseStatusException invalidCredentials() { return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas"); }
}
