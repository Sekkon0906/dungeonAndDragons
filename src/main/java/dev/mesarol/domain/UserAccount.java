package dev.mesarol.domain;

import jakarta.persistence.*;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "user_accounts")
public class UserAccount {
    public enum Role { ORGANIZER, PLAYER }

    @Id
    private UUID id;
    @Column(nullable = false, length = 80)
    private String displayName;
    @Column(nullable = false, unique = true, length = 254)
    private String email;
    @Column(nullable = false)
    private String passwordHash;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;
    @Column(unique = true, length = 255)
    private String oidcSubject;

    protected UserAccount() {}

    public UserAccount(String displayName, String email, String passwordHash, Role role) {
        if (displayName == null || displayName.isBlank() || displayName.strip().length() > 80)
            throw new IllegalArgumentException("Nombre inválido");
        if (email == null || email.isBlank() || email.strip().length() > 254)
            throw new IllegalArgumentException("Correo inválido");
        if (passwordHash == null || passwordHash.isBlank() || role == null)
            throw new IllegalArgumentException("Credenciales y rol obligatorios");
        this.id = UUID.randomUUID();
        this.displayName = displayName.strip();
        this.email = email.strip().toLowerCase(Locale.ROOT);
        this.passwordHash = passwordHash;
        this.role = role;
    }

    public UUID getId() { return id; }
    public String getDisplayName() { return displayName; }
    public Role getRole() { return role; }

    public static UserAccount external(String subject, String name, String email, Role role) {
        var account = new UserAccount(name, email, "!", role);
        account.oidcSubject = subject;
        return account;
    }

    public void setRole(Role role) { this.role = role; }
}
