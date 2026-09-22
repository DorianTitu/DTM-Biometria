package ec.dmt.admin.reporting;

import ec.dmt.admin.user.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="app.reports.demo", havingValue="true")
public class DemoBootstrap implements CommandLineRunner {
    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final String username, password;
    public DemoBootstrap(AppUserRepository users, PasswordEncoder encoder,
        @Value("${DEMO_ADMIN_USERNAME:report.admin}") String username,
        @Value("${DEMO_ADMIN_PASSWORD}") String password) {
        this.users=users; this.encoder=encoder; this.username=username; this.password=password;
    }
    public void run(String... args) {
        if(password.length()<12) throw new IllegalArgumentException("DEMO_ADMIN_PASSWORD requiere al menos 12 caracteres");
        for(String legacy: new String[]{"admin.demo", "inspector.demo"}) users.findByUsername(legacy).ifPresent(u->{u.setActive(false); users.save(u);});
        if(username.equals("admin.demo") || username.equals("inspector.demo")) throw new IllegalArgumentException("Use un usuario distinto a las cuentas heredadas");
        if(users.findByUsername(username).isEmpty()) users.save(new AppUser(username,encoder.encode(password),"ADMINISTRATOR"));
    }
}
