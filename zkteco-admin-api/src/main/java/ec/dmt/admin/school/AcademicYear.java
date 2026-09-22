package ec.dmt.admin.school;
import jakarta.persistence.*;
@Entity @Table(name="academic_year") public class AcademicYear { @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id; @Column(nullable=false,unique=true) private String name; @Column(nullable=false) private String status="OPEN"; protected AcademicYear(){} public AcademicYear(String name){this.name=name;} public Long getId(){return id;} public String getName(){return name;} public String getStatus(){return status;} public void close(){status="CLOSED";} }
