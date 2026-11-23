package dev.mesarol;

import dev.mesarol.domain.*;
import dev.mesarol.persistence.*;
import dev.mesarol.service.RegistrationService;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class RegistrationIntegrationTests {
    @Autowired UserAccountRepository users;
    @Autowired GameSessionRepository sessions;
    @Autowired RegistrationRepository registrations;
    @Autowired RegistrationService service;
    UserAccount organizer;
    UserAccount first;
    UserAccount second;
    GameSession session;

    @BeforeEach
    void prepare() {
        registrations.deleteAll();
        sessions.deleteAll();
        users.deleteAll();
        organizer = users.save(new UserAccount("Directora", "dm@example.test", "test-hash", UserAccount.Role.ORGANIZER));
        first = users.save(new UserAccount("Ana", "ana@example.test", "test-hash", UserAccount.Role.PLAYER));
        second = users.save(new UserAccount("Luis", "luis@example.test", "test-hash", UserAccount.Role.PLAYER));
        session = sessions.save(new GameSession("La cripta", "Aventura introductoria",
                Instant.now().plusSeconds(86400), 1, organizer, Instant.now()));
    }

    @Test void duplicateReservationIsRejected() {
        service.reserve(session.getId(), first.getId());
        assertThrows(IllegalStateException.class, () -> service.reserve(session.getId(), first.getId()));
        assertEquals(1, registrations.countBySessionId(session.getId()));
    }

    @Test void fullSessionIsRejected() {
        service.reserve(session.getId(), first.getId());
        assertThrows(IllegalStateException.class, () -> service.reserve(session.getId(), second.getId()));
    }

    @Test void cancellingReservationReleasesTheSeat() {
        service.reserve(session.getId(), first.getId());
        service.cancelReservation(session.getId(), first.getId());
        service.reserve(session.getId(), second.getId());
        assertTrue(registrations.existsBySessionIdAndPlayerId(session.getId(), second.getId()));
        assertFalse(registrations.existsBySessionIdAndPlayerId(session.getId(), first.getId()));
    }

    @Test void playerCanReserveAgainAfterCancelling() {
        service.reserve(session.getId(), first.getId());
        service.cancelReservation(session.getId(), first.getId());
        service.reserve(session.getId(), first.getId());
        assertEquals(1, registrations.countBySessionId(session.getId()));
    }

    @Test void cancelledSessionRejectsReservations() {
        service.cancelSession(session.getId(), organizer.getId());
        assertThrows(IllegalStateException.class, () -> service.reserve(session.getId(), first.getId()));
    }

    @Test void anotherUserCannotCancelTheSession() {
        assertThrows(IllegalArgumentException.class, () -> service.cancelSession(session.getId(), first.getId()));
        assertEquals(GameSession.Status.SCHEDULED, sessions.findById(session.getId()).orElseThrow().getStatus());
    }

    @Test void organizerCannotTakeAPlayerSeat() {
        assertThrows(IllegalArgumentException.class, () -> service.reserve(session.getId(), organizer.getId()));
        assertEquals(0, registrations.countBySessionId(session.getId()));
    }

    @Test void concurrentRequestsCannotOverbookTheLastSeat() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var ready = new CountDownLatch(2);
            var start = new CountDownLatch(1);
            var a = executor.submit(() -> tryReserve(first.getId(), ready, start));
            var b = executor.submit(() -> tryReserve(second.getId(), ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            int successes = (a.get(15, TimeUnit.SECONDS) ? 1 : 0) + (b.get(15, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, successes);
            assertEquals(1, registrations.countBySessionId(session.getId()));
        }
    }

    private boolean tryReserve(UUID playerId, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Timeout de prueba");
        try {
            service.reserve(session.getId(), playerId);
            return true;
        } catch (IllegalStateException full) {
            assertEquals("No quedan cupos", full.getMessage());
            return false;
        }
    }

    @Test void sessionRejectsPastStartAndInvalidCapacity() {
        var now = Instant.parse("2030-01-01T00:00:00Z");
        assertThrows(IllegalArgumentException.class, () -> new GameSession("Aventura", "Descripción", now, 2, organizer, now));
        assertThrows(IllegalArgumentException.class, () -> new GameSession("Aventura", "Descripción", now.plusSeconds(10), 0, organizer, now));
    }

    @Test void startedSessionIsClosedForReservations() {
        var now = Instant.parse("2030-01-01T00:00:00Z");
        var future = new GameSession("Aventura", "Descripción", now.plusSeconds(10), 2, organizer, now);
        assertThrows(IllegalStateException.class, () -> future.requireOpen(now.plusSeconds(10)));
    }
}
