package ec.dmt.zkteco.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ListenerProperties.class)
public class ListenerConfiguration {
}
