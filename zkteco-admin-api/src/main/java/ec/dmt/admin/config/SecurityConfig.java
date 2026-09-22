package ec.dmt.admin.config;
import ec.dmt.admin.auth.JwtAuthenticationFilter;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.http.HttpMethod;
@Configuration @EnableWebSecurity public class SecurityConfig {
 @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
 @Bean SecurityFilterChain filter(HttpSecurity h, JwtAuthenticationFilter jwt) throws Exception { return h.csrf(c->c.disable()).cors(c->{}).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS)).authorizeHttpRequests(a->a.requestMatchers("/api/auth/login").permitAll().requestMatchers("/api/users/**", "/api/reports/**").hasRole("ADMINISTRATOR").requestMatchers(HttpMethod.GET, "/api/**").hasAnyRole("ADMINISTRATOR", "INSPECTOR").requestMatchers(HttpMethod.POST, "/api/**").hasRole("ADMINISTRATOR").requestMatchers(HttpMethod.PUT, "/api/**").hasRole("ADMINISTRATOR").requestMatchers(HttpMethod.DELETE, "/api/**").hasRole("ADMINISTRATOR").anyRequest().authenticated()).exceptionHandling(e->e.authenticationEntryPoint((request, response, error)->response.sendError(401)).accessDeniedHandler((request,response,error)->response.sendError(403))).httpBasic(b->b.disable()).formLogin(f->f.disable()).addFilterBefore(jwt, UsernamePasswordAuthenticationFilter.class).build(); }
}
