package dev.mesarol.persistence;

import dev.mesarol.domain.GameSession;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.time.Instant;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface GameSessionRepository extends JpaRepository<GameSession, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from GameSession s where s.id = :id")
    Optional<GameSession> findLockedById(@Param("id") UUID id);
    Page<GameSession> findByStatusAndStartsAtAfter(GameSession.Status status, Instant after, Pageable page);
    Page<GameSession> findByOrganizerId(UUID organizerId, Pageable page);
}
