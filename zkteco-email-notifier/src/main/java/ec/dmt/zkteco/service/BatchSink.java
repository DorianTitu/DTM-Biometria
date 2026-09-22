package ec.dmt.zkteco.service;
import ec.dmt.zkteco.domain.AuthenticationEvent;
import java.util.List;
@FunctionalInterface public interface BatchSink { void send(List<AuthenticationEvent> events); }
