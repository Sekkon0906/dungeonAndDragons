package dev.mesarol;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

@Configuration
class SecurityConfiguration {
    @Bean
    SecurityFilterChain security(HttpSecurity http, ObjectProvider<ClientRegistrationRepository> clients,
                                 ObjectProvider<JwtDecoder> jwt) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/", "/index.html", "/*.js", "/*.css", "/vendor/**",
                        "/actuator/health/**", "/api/csrf", "/api/sessions", "/api/sessions/*").permitAll()
                .requestMatchers("/oauth2/**", "/login/**", "/error").permitAll()
                .anyRequest().authenticated())
            .exceptionHandling(errors -> errors.defaultAuthenticationEntryPointFor(
                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                PathPatternRequestMatcher.withDefaults().matcher("/api/**")))
            .logout(logout -> logout.logoutSuccessUrl("/"));
        if (clients.getIfAvailable() != null) {
            http.oauth2Login(login -> login.defaultSuccessUrl("/", true));
        }
        if (jwt.getIfAvailable() != null) {
            http.oauth2ResourceServer(server -> server.jwt(Customizer.withDefaults()));
        }
        return http.build();
    }
}
