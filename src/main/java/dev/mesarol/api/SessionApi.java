package dev.mesarol.api;

import dev.mesarol.domain.GameSession;
import dev.mesarol.domain.UserAccount;
import dev.mesarol.persistence.GameSessionRepository;
import dev.mesarol.persistence.RegistrationRepository;
import dev.mesarol.service.RegistrationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class SessionApi {
    private final GameSessionRepository sessions;
    private final RegistrationRepository reservations;
    private final RegistrationService registration;
    private final CurrentUser current;
    private final Clock clock;

    SessionApi(GameSessionRepository sessions, RegistrationRepository reservations,
               RegistrationService registration, CurrentUser current, Clock clock) {
        this.sessions = sessions;
        this.reservations = reservations;
        this.registration = registration;
        this.current = current;
        this.clock = clock;
    }

    public record SessionInput(@NotBlank @Size(max = 120) String title,
        @NotBlank @Size(max = 2000) String description, @NotNull Instant startsAt,
        @Min(1) @Max(20) int capacity) {}
    public record SessionView(UUID id, String title, String description, Instant startsAt,
        int capacity, long available, String organizer, GameSession.Status status) {}
    public record UserView(UUID id, String name, UserAccount.Role role) {}
    public record ReservationView(UUID id) {}
    public record CsrfView(String headerName, String token) {}

    @GetMapping("/csrf")
    CsrfView csrf(CsrfToken csrf) { return new CsrfView(csrf.getHeaderName(), csrf.getToken()); }

    @GetMapping("/me")
    UserView me(Authentication auth) {
        var user = current.resolve(auth);
        return new UserView(user.getId(), user.getDisplayName(), user.getRole());
    }

    @GetMapping("/sessions")
    @Transactional(readOnly = true)
    List<SessionView> upcoming(@RequestParam(defaultValue = "0") int page) {
        return sessions.findByStatusAndStartsAtAfter(GameSession.Status.SCHEDULED, clock.instant(),
                page(page, "startsAt")).map(this::view).getContent();
    }

    @GetMapping("/sessions/{id}")
    @Transactional(readOnly = true)
    SessionView detail(@PathVariable UUID id) { return view(find(id)); }

    @GetMapping("/me/sessions")
    @Transactional
    List<SessionView> organized(Authentication auth, @RequestParam(defaultValue = "0") int page) {
        var user = require(auth, UserAccount.Role.ORGANIZER);
        return sessions.findByOrganizerId(user.getId(), page(page, "startsAt")).map(this::view).getContent();
    }

    @GetMapping("/me/registrations")
    @Transactional
    List<SessionView> reserved(Authentication auth, @RequestParam(defaultValue = "0") int page) {
        var user = require(auth, UserAccount.Role.PLAYER);
        return reservations.findByPlayerId(user.getId(), page(page, "createdAt"))
                .map(r -> view(r.getSession())).getContent();
    }

    @PostMapping("/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    SessionView create(Authentication auth, @Valid @RequestBody SessionInput input) {
        var organizer = require(auth, UserAccount.Role.ORGANIZER);
        return view(sessions.save(draft(input, organizer)));
    }

    @PutMapping("/sessions/{id}")
    @Transactional
    SessionView edit(Authentication auth, @PathVariable UUID id, @Valid @RequestBody SessionInput input) {
        var organizer = require(auth, UserAccount.Role.ORGANIZER);
        var session = sessions.findLockedById(id).orElseThrow(SessionApi::missing);
        owner(session, organizer);
        if (reservations.countBySessionId(id) > input.capacity())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya hay más jugadores inscritos que esos cupos");
        session.edit(draft(input, organizer), clock.instant());
        return view(session);
    }

    @PostMapping("/sessions/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    void cancel(Authentication auth, @PathVariable UUID id) {
        var organizer = require(auth, UserAccount.Role.ORGANIZER);
        var session = sessions.findLockedById(id).orElseThrow(SessionApi::missing);
        owner(session, organizer);
        session.cancel(organizer.getId(), clock.instant());
    }

    @PostMapping("/sessions/{id}/registrations")
    @ResponseStatus(HttpStatus.CREATED)
    ReservationView reserve(Authentication auth, @PathVariable UUID id) {
        var player = require(auth, UserAccount.Role.PLAYER);
        return new ReservationView(registration.reserve(id, player.getId()));
    }

    @DeleteMapping("/sessions/{id}/registrations/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void release(Authentication auth, @PathVariable UUID id) {
        registration.cancelReservation(id, require(auth, UserAccount.Role.PLAYER).getId());
    }

    private SessionView view(GameSession s) {
        long free = s.getStatus() == GameSession.Status.CANCELLED || !s.getStartsAt().isAfter(clock.instant())
                ? 0 : s.getCapacity() - reservations.countBySessionId(s.getId());
        return new SessionView(s.getId(), s.getTitle(), s.getDescription(), s.getStartsAt(),
                s.getCapacity(), free, s.getOrganizer().getDisplayName(), s.getStatus());
    }

    private GameSession draft(SessionInput input, UserAccount organizer) {
        return new GameSession(input.title(), input.description(), input.startsAt(),
                input.capacity(), organizer, clock.instant());
    }

    private static PageRequest page(int page, String sort) {
        if (page < 0) throw new IllegalArgumentException("Página inválida");
        return PageRequest.of(page, 20, Sort.by(sort, "id"));
    }

    private UserAccount require(Authentication auth, UserAccount.Role role) {
        var user = current.resolve(auth);
        if (user.getRole() != role) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tu rol no permite esta acción");
        return user;
    }

    private GameSession find(UUID id) { return sessions.findById(id).orElseThrow(SessionApi::missing); }
    private static ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Sesión inexistente"); }
    private static void owner(GameSession session, UserAccount user) {
        if (!session.getOrganizer().getId().equals(user.getId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el organizador de esta partida puede cambiarla");
    }
}
