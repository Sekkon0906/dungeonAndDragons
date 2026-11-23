package dev.mesarol.persistence;

import dev.mesarol.domain.UserAccount;
import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {
    Optional<UserAccount> findByOidcSubject(String subject);
}
