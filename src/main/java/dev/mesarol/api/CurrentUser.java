package dev.mesarol.api;

import dev.mesarol.domain.UserAccount;
import dev.mesarol.persistence.UserAccountRepository;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CurrentUser {
    private final UserAccountRepository users;
    private final Set<String> organizers;

    CurrentUser(UserAccountRepository users, @Value("${MESAROL_ORGANIZERS:}") String organizers) {
        this.users = users;
        this.organizers = Arrays.stream(organizers.split(","))
                .map(s -> s.strip().toLowerCase(Locale.ROOT)).filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Transactional
    public UserAccount resolve(Authentication authentication) {
        Map<String, Object> claims;
        if (authentication instanceof JwtAuthenticationToken jwt) claims = jwt.getTokenAttributes();
        else if (authentication != null && authentication.getPrincipal() instanceof OidcUser user) claims = user.getClaims();
        else throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Inicia sesión");

        String subject = Objects.toString(claims.get("sub"), "");
        String issuer = Objects.toString(claims.get("iss"), "");
        String email = Objects.toString(claims.get("email"), "").strip().toLowerCase(Locale.ROOT);
        if (subject.isBlank() || issuer.isBlank() || !email.contains("@") || email.length() > 254 ||
                !Boolean.TRUE.equals(claims.get("email_verified"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Se requiere un correo verificado");
        }
        // Namespace the subject by issuer so a provider change cannot link unrelated accounts.
        String externalId = java.util.UUID.nameUUIDFromBytes(
                (issuer + "\n" + subject).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        String name = Objects.toString(claims.get("name"), "").strip();
        if (name.isBlank()) name = email.substring(0, email.indexOf('@'));
        if (name.isBlank()) name = "Aventurero";
        if (name.length() > 80) name = name.substring(0, 80);
        var role = organizers.contains(email) ? UserAccount.Role.ORGANIZER : UserAccount.Role.PLAYER;
        var existing = users.findByOidcSubject(externalId);
        if (existing.isPresent()) {
            var account = existing.get();
            account.setRole(role);
            return account;
        }
        return users.saveAndFlush(UserAccount.external(externalId, name, email, role));
    }
}
