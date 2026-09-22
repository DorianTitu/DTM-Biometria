package ec.dmt.zkteco.service;
import ec.dmt.zkteco.config.RabbitConfig; import ec.dmt.zkteco.domain.AuthenticationEvent; import org.springframework.amqp.rabbit.core.RabbitTemplate; import org.springframework.stereotype.Service;
@Service public class AuthEventPublisher { private final RabbitTemplate rabbit; public AuthEventPublisher(RabbitTemplate rabbit){this.rabbit=rabbit;} public void publish(AuthenticationEvent e){rabbit.convertAndSend(RabbitConfig.QUEUE,e);}}
