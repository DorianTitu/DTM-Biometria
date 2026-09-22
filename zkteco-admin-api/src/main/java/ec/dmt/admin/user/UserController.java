package ec.dmt.admin.user;
import jakarta.validation.Valid; import jakarta.validation.constraints.*; import org.springframework.http.*; import org.springframework.security.crypto.password.PasswordEncoder; import org.springframework.web.bind.annotation.*; import org.springframework.web.server.ResponseStatusException; import java.util.*;
@RestController @RequestMapping("/api/users") public class UserController {
 private final AppUserRepository repo; private final PasswordEncoder encoder;
 public UserController(AppUserRepository repo,PasswordEncoder encoder){this.repo=repo;this.encoder=encoder;}
 @GetMapping public List<View> list(){return repo.findAll().stream().map(u->new View(u.getId(),u.getUsername(),u.getRole(),u.isActive())).toList();}
 @PostMapping("/inspectors") public ResponseEntity<View> createInspector(@Valid @RequestBody Input in){if(repo.findByUsername(in.username().trim()).isPresent())throw new ResponseStatusException(HttpStatus.CONFLICT,"El usuario ya existe"); AppUser u=repo.save(new AppUser(in.username().trim(),encoder.encode(in.password()),"INSPECTOR"));return ResponseEntity.status(201).body(new View(u.getId(),u.getUsername(),u.getRole(),u.isActive()));}
 @PatchMapping("/{id}/active") public View setActive(@PathVariable Long id,@RequestBody Active input){AppUser u=repo.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Usuario no encontrado"));u.setActive(input.active());return new View(u.getId(),u.getUsername(),u.getRole(),repo.save(u).isActive());}
 public record Input(@NotBlank String username,@NotBlank @Size(min=10,message="La contraseña debe tener al menos 10 caracteres") String password){} public record Active(boolean active){} public record View(Long id,String username,String role,boolean active){}
}
