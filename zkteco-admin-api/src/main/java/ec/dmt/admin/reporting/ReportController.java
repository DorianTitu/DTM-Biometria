package ec.dmt.admin.reporting;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.*;
import java.time.*;
import java.util.*;

@RestController
@RequestMapping("/api/reports")
@ConditionalOnProperty(name = "app.reports.demo", havingValue = "true")
public class ReportController {
    static final List<String> COURSES = List.of("Octavo EGB", "Noveno EGB", "Décimo EGB", "Primero BGU");
    static final List<String> STATES = List.of("PRESENT", "LATE", "ABSENT");
    record Row(String id, LocalDate date, String student, String biometricId, String course, String parallel, String state, String arrival) {}
    record Summary(long total, long present, long late, long absent) {}
    record Report(boolean demo, String timezone, Summary summary, List<Row> rows) {}

    @GetMapping("/options")
    public Map<String, Object> options() {
        return Map.of("demo", true, "courses", COURSES, "parallels", List.of("A", "B"), "today", LocalDate.now(ZoneId.of("America/Guayaquil")));
    }
    @GetMapping("/attendance")
    public Report attendance(@RequestParam LocalDate from, @RequestParam LocalDate to,
            @RequestParam(defaultValue = "") String course, @RequestParam(defaultValue = "") String parallel,
            @RequestParam(defaultValue = "") String state, @RequestParam(defaultValue = "") String search) {
        var rows = rows(from, to, course, parallel, state, search);
        return new Report(true, "America/Guayaquil", new Summary(rows.size(), count(rows,"PRESENT"),count(rows,"LATE"),count(rows,"ABSENT")), rows);
    }
    private long count(List<Row> rows, String state) { return rows.stream().filter(r -> r.state().equals(state)).count(); }
    List<Row> rows(LocalDate from, LocalDate to, String course, String parallel, String state, String search) {
        if (from.isAfter(to) || java.time.temporal.ChronoUnit.DAYS.between(from,to)>92)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seleccione un rango de hasta 93 días.");
        if ((!course.isEmpty() && !COURSES.contains(course)) || (!parallel.isEmpty() && !List.of("A","B").contains(parallel)) || (!state.isEmpty() && !STATES.contains(state)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Filtro inválido.");
        String[] names = {"Ana", "Mateo", "Sofía", "Daniel", "Valentina", "Lucas", "Camila", "Emilio"};
        String[] surnames = {"Andrade", "Castro", "Molina", "Vega", "Rojas", "Cabrera", "Salazar", "Torres"};
        List<Row> result = new ArrayList<>();
        for (LocalDate day=from; !day.isAfter(to); day=day.plusDays(1)) {
            if (day.getDayOfWeek().getValue()>5) continue;
            for (int i=0;i<64;i++) {
                String c=COURSES.get(i/16), p=i%16<8?"A":"B", name=names[i%8]+" "+surnames[i/8];
                int seed=Math.floorMod(day.toEpochDay()+i*7,20);
                String st=seed<3?"ABSENT":seed<6?"LATE":"PRESENT", id=String.valueOf(1001+i);
                if ((!course.isEmpty()&&!c.equals(course)) || (!parallel.isEmpty()&&!p.equals(parallel)) || (!state.isEmpty()&&!st.equals(state)) || !(name+" "+id).toLowerCase(Locale.ROOT).contains(search.trim().toLowerCase(Locale.ROOT))) continue;
                String arrival=st.equals("ABSENT")?null:st.equals("LATE")?String.format("08:%02d",1+seed*3):String.format("07:%02d",30+seed);
                result.add(new Row(day+"-"+id,day,name,id,c,p,st,arrival));
            }
        }
        return result;
    }
    @GetMapping(value="/attendance.csv", produces="text/csv;charset=UTF-8")
    public ResponseEntity<String> csv(@RequestParam LocalDate from, @RequestParam LocalDate to,
            @RequestParam(defaultValue="") String course, @RequestParam(defaultValue="") String parallel,
            @RequestParam(defaultValue="") String state, @RequestParam(defaultValue="") String search) {
        StringBuilder csv=new StringBuilder("\uFEFFFecha,Estudiante,ID biométrico,Curso,Paralelo,Estado,Hora (Ecuador)\r\n");
        for (Row r: rows(from,to,course,parallel,state,search)) {
            String label=r.state().equals("PRESENT")?"Puntual":r.state().equals("LATE")?"Atraso":"Ausente";
            csv.append(String.join(",", List.of(r.date().toString(),r.student(),r.biometricId(),r.course(),r.parallel(),label,r.arrival()==null?"":r.arrival()).stream().map(v->"\""+v.replace("\"","\"\"")+"\"").toList())).append("\r\n");
        }
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=asistencia-demo.csv").body(csv.toString());
    }
}
