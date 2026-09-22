package ec.dmt.zkteco.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "zkteco.listener")
public record ListenerProperties(String serverVersion, String pushProtocolVersion) {
    public ListenerProperties {
        serverVersion = serverVersion == null ? "3.0.1" : serverVersion;
        pushProtocolVersion = pushProtocolVersion == null ? "2.4.1" : pushProtocolVersion;
    }
}
