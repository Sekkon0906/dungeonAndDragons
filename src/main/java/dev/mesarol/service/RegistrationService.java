package dev.mesarol.service;

import dev.mesarol.domain.Registration;
import dev.mesarol.persistence.*;
import java.time.Clock;
import java.util.UUID;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {
    private final GameSessionRepository sessions;
    private final UserAccountRepository users;
    private final RegistrationRepository registrations;
    private final Clock clock;

    public RegistrationService(GameSessionRepository sessions, UserAccountRepository users,
                               RegistrationRepository registrations, Clock clock) {
        this.sessions = sessions;
        this.users = users;
        this.registrations = registrations;
        this.clock = clock;
    }

    // Todas las operaciones que alteren cupos deben bloquear primero esta misma sesión.
    @Transactional
    public UUID reserve(UUID sessionId, UUID playerId) {
        var session = sessions.findLockedById(sessionId)
                .orElseThrow(() -> new NoSuchElementException("Sesión inexistente"));
        session.requireOpen(clock.instant());
        if (registrations.existsBySessionIdAndPlayerId(sessionId, playerId))
            throw new IllegalStateException("Ya estás inscrito");
        if (registrations.countBySessionId(sessionId) >= session.getCapacity())
            throw new IllegalStateException("No quedan cupos");
        var player = users.findById(playerId)
                .orElseThrow(() -> new IllegalArgumentException("Jugador inexistente"));
        return registrations.saveAndFlush(new Registration(session, player, clock.instant())).getId();
    }

    @Transactional
    public void cancelReservation(UUID sessionId, UUID playerId) {
        var session = sessions.findLockedById(sessionId)
                .orElseThrow(() -> new NoSuchElementException("Sesión inexistente"));
        session.requireOpen(clock.instant());
        var registration = registrations.findBySessionIdAndPlayerId(sessionId, playerId)
                .orElseThrow(() -> new NoSuchElementException("Inscripción inexistente"));
        registrations.delete(registration);
    }

    @Transactional
    public void cancelSession(UUID sessionId, UUID organizerId) {
        var session = sessions.findLockedById(sessionId)
                .orElseThrow(() -> new NoSuchElementException("Sesión inexistente"));
        session.cancel(organizerId, clock.instant());
    }
}
