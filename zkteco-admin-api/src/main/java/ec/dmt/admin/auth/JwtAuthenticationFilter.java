package ec.dmt.admin.auth;
import io.jsonwebtoken.Claims;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;
@Component public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService; public JwtAuthenticationFilter(JwtService jwtService) { this.jwtService = jwtService; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException { String header = request.getHeader("Authorization"); if (header != null && header.startsWith("Bearer ") && SecurityContextHolder.getContext().getAuthentication() == null) try { Claims claims = jwtService.parse(header.substring(7)); String role = claims.get("role", String.class); if (role != null && claims.getSubject() != null) SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(claims.getSubject(), null, List.of(new SimpleGrantedAuthority("ROLE_" + role)))); } catch (RuntimeException ignored) { } chain.doFilter(request, response); }
}
