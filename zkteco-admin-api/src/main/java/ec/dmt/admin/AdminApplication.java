package ec.dmt.admin;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class AdminApplication { public static void main(String[] a) { SpringApplication.run(AdminApplication.class, a); } }
