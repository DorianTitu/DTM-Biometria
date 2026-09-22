package ec.dmt.admin.auth;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/auth") public class AuthController {
    private final AuthService authService; private final LoginRateLimiter loginRateLimiter;
    public AuthController(AuthService authService, LoginRateLimiter loginRateLimiter) { this.authService = authService; this.loginRateLimiter = loginRateLimiter; }
    @PostMapping("/login") public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        if (!loginRateLimiter.allow(clientIp(httpRequest))) return ResponseEntity.status(429).body(null);
        return ResponseEntity.ok(authService.login(request.username(), request.password()));
    }
    private String clientIp(HttpServletRequest request) { return request.getRemoteAddr(); }
    public record LoginRequest(@NotBlank String username, @NotBlank String password) { }
    public record LoginResponse(String token, String role, String username, long expiresAt) { }
}
