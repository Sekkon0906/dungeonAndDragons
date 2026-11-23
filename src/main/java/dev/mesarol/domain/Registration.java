package dev.mesarol.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "registrations", uniqueConstraints = @UniqueConstraint(columnNames = {"session_id", "player_id"}))
public class Registration {
    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private GameSession session;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", nullable = false)
    private UserAccount player;
    @Column(nullable = false)
    private Instant createdAt;

    protected Registration() {}

    public Registration(GameSession session, UserAccount player, Instant now) {
        if (player == null || player.getRole() != UserAccount.Role.PLAYER)
            throw new IllegalArgumentException("Se requiere un jugador");
        session.requireOpen(now);
        this.id = UUID.randomUUID();
        this.session = session;
        this.player = player;
        this.createdAt = now;
    }

    public UUID getId() { return id; }
    public GameSession getSession() { return session; }
}
