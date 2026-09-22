package ec.dmt.admin.auth;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;
@RestController @RequestMapping("/api/auth") public class CurrentUserController {
    @GetMapping("/me") public Map<String, String> me(Authentication authentication) { return Map.of("username", authentication.getName(), "role", authentication.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "")); }
}
