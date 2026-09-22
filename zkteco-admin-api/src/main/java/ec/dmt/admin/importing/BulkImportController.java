package ec.dmt.admin.importing;

import ec.dmt.admin.school.*;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/api/bulk")
public class BulkImportController {
    private final CourseRepository courses; private final SchoolParallelRepository parallels; private final StudentRepository students; private final GuardianRepository guardians;
    public BulkImportController(CourseRepository courses, SchoolParallelRepository parallels, StudentRepository students, GuardianRepository guardians) { this.courses=courses; this.parallels=parallels; this.students=students; this.guardians=guardians; }
    @GetMapping(value="/template/{type}", produces="text/csv") public ResponseEntity<String> template(@PathVariable String type) {
        String csv = switch(type) { case "students" -> "biometric_user_id,first_name,last_name,course,parallel,active\n1001,Ana,Pérez,Primero,A,true\n"; case "guardians" -> "student_biometric_id,first_name,last_name,email,active\n1001,María,Pérez,maria@example.com,true\n"; case "courses" -> "course,parallels\nPrimero,A|B|C\nSegundo,A|B\n"; default -> throw new IllegalArgumentException("Plantilla no encontrada"); };
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=plantilla-"+type+".csv").body(csv);
    }
    @PostMapping(value="/import/{type}", consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @Transactional public Result importCsv(@PathVariable String type, @RequestPart("file") MultipartFile file) throws IOException {
        List<String[]> rows = parse(file); if(rows.isEmpty()) return new Result(0,List.of("El archivo no contiene registros")); List<String> errors=new ArrayList<>(); int imported=0;
        for(int i=1;i<rows.size();i++) try { String[] r=rows.get(i); if(type.equals("courses")){ Course c=new Course(value(r,0),true); courses.save(c); for(String name:value(r,1).split("\\|")) parallels.save(new SchoolParallel(c,name.trim(),true)); } else if(type.equals("students")){ Course c=courses.findAll().stream().filter(x->x.getName().equalsIgnoreCase(value(r,3))).findFirst().orElseThrow(); SchoolParallel p=parallels.findAll().stream().filter(x->x.getCourse().getId().equals(c.getId())&&x.getName().equalsIgnoreCase(value(r,4))).findFirst().orElseThrow(); students.save(new Student(value(r,0),value(r,1),value(r,2),p,active(r,5))); } else if(type.equals("guardians")){ Student s=students.findAll().stream().filter(x->x.getBiometricUserId().equals(value(r,0))).findFirst().orElseThrow(); guardians.save(new Guardian(s,value(r,1),value(r,2),value(r,3),active(r,4))); } else throw new IllegalArgumentException(); imported++; } catch(Exception e){ errors.add("Fila "+(i+1)+": revise valores duplicados o relaciones inexistentes"); }
        return new Result(imported,errors);
    }
    private List<String[]> parse(MultipartFile file) throws IOException { List<String[]> result=new ArrayList<>(); try(var reader=new BufferedReader(new InputStreamReader(file.getInputStream(),StandardCharsets.UTF_8))){ String line; while((line=reader.readLine())!=null) if(!line.isBlank()) result.add(line.replace("\uFEFF","").split(",",-1)); } return result; }
    private String value(String[] row,int index){ if(index>=row.length||row[index].isBlank()) throw new IllegalArgumentException(); return row[index].trim(); } private boolean active(String[] r,int i){return i>=r.length||r[i].isBlank()||Boolean.parseBoolean(r[i]);}
    public record Result(int imported,List<String> errors){}
}
