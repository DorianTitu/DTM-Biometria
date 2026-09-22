package ec.dmt.admin.config;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
@Configuration public class CorsConfig implements WebMvcConfigurer { @Override public void addCorsMappings(CorsRegistry r){ r.addMapping("/api/**").allowedOrigins("http://localhost:3000").allowedMethods("GET","POST","PUT","DELETE","OPTIONS").allowedHeaders("*"); } }
