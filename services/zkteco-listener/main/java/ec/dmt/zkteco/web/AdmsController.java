package ec.dmt.zkteco.web;

import ec.dmt.zkteco.config.ListenerProperties;
import ec.dmt.zkteco.service.AuthenticationLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.io.IOException;

@RestController
@RequestMapping("/iclock")
public class AdmsController {
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

    @PostMapping(value = "/cdata", consumes = MediaType.ALL_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> cdata(@RequestParam(name = "SN", defaultValue = "") String serial,
                                         @RequestParam(name = "table", defaultValue = "") String table,
                                         HttpServletRequest request) throws IOException {
        if (!("ATTLOG".equalsIgnoreCase(table) || "RTLOG".equalsIgnoreCase(table))) {
            return ResponseEntity.ok("OK");
        }
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
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

    /** Some firmware versions probe cdata with GET before posting attendance. */
    @GetMapping(value = "/cdata", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> cdataProbe() {
        return ResponseEntity.ok("OK");
    }

    @GetMapping(value = "/getrequest", produces = MediaType.TEXT_PLAIN_VALUE)
    public String getRequest(@RequestParam(name = "SN", defaultValue = "") String serial) {
        return "GET OPTION FROM: " + serial + "\n"
                + "ATTLOGStamp=0\nOPERLOGStamp=0\nRealtime=1\n"
                + "TransFlag=TransData AttLog\nServerVer=" + properties.serverVersion() + "\n"
                + "PushProtVer=" + properties.pushProtocolVersion() + "\n";
    }

    @PostMapping(value = "/registry", consumes = MediaType.ALL_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public String registry() {
        return "OK";
    }

    @PostMapping(value = "/reg", consumes = MediaType.ALL_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public String reg() {
        return "OK";
    }

    @GetMapping(value = {"/registry", "/reg"}, produces = MediaType.TEXT_PLAIN_VALUE)
    public String registrationProbe() {
        return "OK";
    }

    private int integer(String value) {
        try { return Integer.parseInt(value); } catch (NumberFormatException ex) { return 0; }
    }
}
