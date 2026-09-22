package ec.dmt.zkteco.web;

import ec.dmt.zkteco.config.ListenerProperties;
import ec.dmt.zkteco.service.AuthenticationLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/iclock")
public class AdmsController {
    private static final Logger log = LoggerFactory.getLogger(AdmsController.class);
    private static final Pattern KV = Pattern.compile("(?:^|\\s)(time|pin|verifytype|event|inoutstatus)=([^\\s]+(?:\\s+[^\\s=]+)?)");
    private final AuthenticationLogService service;
    private final ListenerProperties properties;

    public AdmsController(AuthenticationLogService service, ListenerProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @PostMapping("/notify")
    public ResponseEntity<String> notifyEmail(@RequestParam String userId) {
        System.out.println("[EMAIL_SIMULADO] Hola usuario id: " + userId + " - correo enviado");
        return ResponseEntity.ok("OK");
    }

    @PostMapping(value = "/cdata", consumes = MediaType.ALL_VALUE, produces = MediaType.ALL_VALUE)
    public ResponseEntity<String> cdata(@RequestParam(name = "SN", defaultValue = "") String serial,
                                         @RequestParam(name = "table", defaultValue = "") String table,
                                         HttpServletRequest request) throws IOException {
        log.info("[ADMS_REQUEST] method={} path={} remote={} SN={} table={} contentType={}", request.getMethod(), request.getRequestURI(), request.getRemoteAddr(), serial, table, request.getContentType());
        if (!isSupportedLog(table)) {
            return ResponseEntity.ok("OK");
        }
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (isSecurityPayload(line)) {
                    parseSecurityEvent(serial, line);
                    continue;
                }
                String[] fields = line.trim().split("\\t", -1);
                if (fields.length < 2) {
                    fields = line.trim().split("\\s+", 4);
                    if (fields.length >= 3) fields = new String[] { fields[0], fields[1] + " " + fields[2], fields.length > 3 ? fields[3] : "0", "0" };
                }
                if (fields.length >= 2 && !fields[0].isBlank()) {
                    int verifyType = fields.length > 3 ? integer(fields[3]) : 0;
                    int status = fields.length > 2 ? integer(fields[2]) : 0;
                    service.record(serial, fields[0], fields[1], verifyType, status);
                }
            }
        }
        return ResponseEntity.ok("OK");
    }

    /** A&C/CA firmware variants use different table names; normalize them through the same pipeline. */
    @PostMapping(value = "/acdata", consumes = MediaType.ALL_VALUE, produces = MediaType.ALL_VALUE)
    public ResponseEntity<String> acdata(@RequestParam(name = "SN", defaultValue = "") String serial,
                                         @RequestParam(name = "table", defaultValue = "ACLOG") String table,
                                         HttpServletRequest request) throws IOException {
        log.info("[AC_REQUEST] method={} path={} remote={} SN={} table={} contentType={}", request.getMethod(), request.getRequestURI(), request.getRemoteAddr(), serial, table, request.getContentType());
        return cdata(serial, table, request);
    }

    private boolean isSupportedLog(String table) {
        return "ATTLOG".equalsIgnoreCase(table) || "RTLOG".equalsIgnoreCase(table)
                || "TRANSACTION".equalsIgnoreCase(table) || "ACC_ATT_LOG".equalsIgnoreCase(table)
                || "ACLOG".equalsIgnoreCase(table) || "ACLOGDATA".equalsIgnoreCase(table)
                || "ACCESS".equalsIgnoreCase(table) || "ACRLOG".equalsIgnoreCase(table);
    }

    private boolean isSecurityPayload(String line) {
        return line.contains("pin=") && line.contains("time=");
    }

    private void parseSecurityEvent(String serial, String line) {
        String time = value(line, "time");
        String pin = value(line, "pin");
        int verify = integer(value(line, "verifytype"));
        int event = integer(value(line, "event"));
        if (pin == null || pin.isBlank()) {
            log.warn("[CA_EVENT_IGNORED] No se encontró pin en payload: {}", line);
            return;
        }
        log.info("[CA_EVENT] SN={} pin={} event={} inoutstatus={} verifytype={}", serial, pin, event, value(line, "inoutstatus"), verify);
        service.record(serial, pin, time, verify, event);
    }

    private String value(String line, String key) {
        String expression = "time".equals(key)
                ? "(?:^|\\s)time=([0-9]{4}-[0-9]{2}-[0-9]{2}\\s+[0-9]{2}:[0-9]{2}:[0-9]{2})"
                : "(?:^|\\s)" + key + "=([^\\s]+)";
        Matcher matcher = Pattern.compile(expression).matcher(line);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    /** Some firmware versions probe cdata with GET before posting attendance. */
    @GetMapping(value = "/cdata", produces = MediaType.ALL_VALUE)
    public ResponseEntity<String> cdataProbe() {
        log.debug("[ADMS_PROBE] GET /iclock/cdata");
        return ResponseEntity.ok("OK");
    }

    @GetMapping(value = "/getrequest", produces = MediaType.ALL_VALUE)
    public String getRequest(@RequestParam(name = "SN", defaultValue = "") String serial) {
        log.debug("[ADMS_GETREQUEST] SN={}", serial);
        return "GET OPTION FROM: " + serial + "\n"
                + "ATTLOGStamp=0\nOPERLOGStamp=0\nRealtime=1\n"
                + "TransFlag=TransData AttLog\nServerVer=" + properties.serverVersion() + "\n"
                + "PushProtVer=" + properties.pushProtocolVersion() + "\n";
    }

    @PostMapping(value = "/registry", consumes = MediaType.ALL_VALUE, produces = MediaType.ALL_VALUE)
    public String registry(@RequestParam(name = "SN", defaultValue = "") String serial) {
        log.info("[ADMS_REGISTRY] SN={}", serial);
        return "RegistryCode= " + UUID.randomUUID().toString().replace("-", "");
    }

    @PostMapping(value = "/reg", consumes = MediaType.ALL_VALUE, produces = MediaType.ALL_VALUE)
    public String reg() {
        return "OK";
    }

    @PostMapping(value = "/push", consumes = MediaType.ALL_VALUE, produces = MediaType.ALL_VALUE)
    public String pushConfiguration(@RequestParam(name = "SN", defaultValue = "") String serial) {
        log.info("[ADMS_PUSH_CONFIG] SN={}", serial);
        return "ServerVersion=3.0.1\nServerName=DTM-Biometrico-ZKTeco\n"
                + "ErrorDelay=60\nRequestDelay=10\nTransTimes=00:00\nTransInterval=1\n"
                + "TransTables=User Transaction\nRealtime=1\nTimeoutSec=10\n";
    }

    @GetMapping(value = {"/registry", "/reg"}, produces = MediaType.ALL_VALUE)
    public String registrationProbe() {
        return "OK";
    }

    private int integer(String value) {
        try { return Integer.parseInt(value); } catch (NumberFormatException ex) { return 0; }
    }
}
