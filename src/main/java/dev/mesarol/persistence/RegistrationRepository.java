package dev.mesarol.persistence;

import dev.mesarol.domain.Registration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistrationRepository extends JpaRepository<Registration, UUID> {
    long countBySessionId(UUID sessionId);
    boolean existsBySessionIdAndPlayerId(UUID sessionId, UUID playerId);
    Optional<Registration> findBySessionIdAndPlayerId(UUID sessionId, UUID playerId);
    Page<Registration> findByPlayerId(UUID playerId, Pageable page);
}
