package ec.dmt.zkteco.config;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
@Configuration public class RabbitConfig { public static final String QUEUE="zkteco.auth.events"; @Bean Queue authQueue(){return new Queue(QUEUE,true);} @Bean Jackson2JsonMessageConverter messageConverter(){return new Jackson2JsonMessageConverter();} }
