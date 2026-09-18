package ec.dmt.zkteco;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ZktecoAuthListenerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ZktecoAuthListenerApplication.class, args);
    }
}
