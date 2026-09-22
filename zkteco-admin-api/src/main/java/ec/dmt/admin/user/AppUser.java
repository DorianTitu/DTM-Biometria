package ec.dmt.admin.user;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name = "app_user") public class AppUser {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true) private String username;
    @Column(name = "password_hash", nullable = false) private String passwordHash;
    @Column(nullable = false) private String role;
    @Column(nullable = false) private boolean active;
    @Column(name = "failed_attempts", nullable = false) private int failedAttempts;
    @Column(name = "locked_until") private Instant lockedUntil;
    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
    protected AppUser(){} public AppUser(String username,String passwordHash,String role){this.username=username;this.passwordHash=passwordHash;this.role=role;this.active=true;}
    public Long getId(){return id;} public String getUsername() { return username; } public String getPasswordHash() { return passwordHash; } public String getRole() { return role; } public boolean isActive() { return active; } public int getFailedAttempts() { return failedAttempts; } public Instant getLockedUntil() { return lockedUntil; } public void setActive(boolean active){this.active=active;}
    public void setFailedAttempts(int failedAttempts) { this.failedAttempts = failedAttempts; } public void setLockedUntil(Instant lockedUntil) { this.lockedUntil = lockedUntil; }
}
