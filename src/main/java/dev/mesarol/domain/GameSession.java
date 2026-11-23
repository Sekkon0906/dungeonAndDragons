package dev.mesarol.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "game_sessions")
public class GameSession {
    public enum Status { SCHEDULED, CANCELLED }

    @Id
    private UUID id;
    @Column(nullable = false, length = 120)
    private String title;
    @Column(nullable = false, length = 2000)
    private String description;
    @Column(nullable = false)
    private Instant startsAt;
    @Column(nullable = false)
    private int capacity;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organizer_id", nullable = false)
    private UserAccount organizer;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    protected GameSession() {}

    public GameSession(String title, String description, Instant startsAt, int capacity,
                       UserAccount organizer, Instant now) {
        if (title == null || title.isBlank() || title.strip().length() > 120)
            throw new IllegalArgumentException("Título inválido");
        if (description == null || description.isBlank() || description.length() > 2000)
            throw new IllegalArgumentException("Descripción inválida");
        if (startsAt == null || !startsAt.isAfter(now))
            throw new IllegalArgumentException("La sesión debe empezar en el futuro");
        if (capacity < 1 || capacity > 20)
            throw new IllegalArgumentException("Los cupos deben estar entre 1 y 20");
        if (organizer == null || organizer.getRole() != UserAccount.Role.ORGANIZER)
            throw new IllegalArgumentException("Se requiere un organizador");
        this.id = UUID.randomUUID();
        this.title = title.strip();
        this.description = description.strip();
        this.startsAt = startsAt;
        this.capacity = capacity;
        this.organizer = organizer;
        this.status = Status.SCHEDULED;
    }

    public void requireOpen(Instant now) {
        if (status != Status.SCHEDULED) throw new IllegalStateException("La sesión está cancelada");
        if (!startsAt.isAfter(now)) throw new IllegalStateException("La sesión ya comenzó");
    }

    public void cancel(UUID organizerId, Instant now) {
        if (!organizer.getId().equals(organizerId))
            throw new IllegalArgumentException("Solo el organizador puede cancelar la sesión");
        requireOpen(now);
        status = Status.CANCELLED;
    }

    public UUID getId() { return id; }
    public int getCapacity() { return capacity; }
    public Status getStatus() { return status; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public Instant getStartsAt() { return startsAt; }
    public UserAccount getOrganizer() { return organizer; }

    public void edit(GameSession draft, Instant now) {
        requireOpen(now);
        title = draft.title;
        description = draft.description;
        startsAt = draft.startsAt;
        capacity = draft.capacity;
    }
}
