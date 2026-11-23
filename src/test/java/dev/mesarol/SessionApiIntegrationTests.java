package dev.mesarol;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "MESAROL_ORGANIZERS=directora@api.test,colega@api.test")
@ActiveProfiles("test")
@AutoConfigureMockMvc
class SessionApiIntegrationTests {
    @Autowired MockMvc mvc;

    @Test
    void requiresAuthenticationAndCsrfForWrites() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/sessions")).andExpect(status().isOk());
        mvc.perform(post("/api/sessions").with(account("directora")))
                .andExpect(status().isForbidden());
    }

    @Test
    void onlyTheOwnerCanEditAndOnlyPlayersCanReserve() throws Exception {
        var input = """
                {"title":"Partida de prueba","description":"Una aventura","startsAt":"%s","capacity":1}
                """.formatted(Instant.now().plusSeconds(3600));
        var location = mvc.perform(post("/api/sessions").with(account("directora")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(input))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        var id = tools.jackson.databind.json.JsonMapper.shared().readTree(location).get("id").asString();
        var path = "/api/sessions/" + id;
        mvc.perform(put(path).with(account("colega")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(input)).andExpect(status().isForbidden());
        mvc.perform(post(path + "/registrations").with(account("directora")).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post(path + "/registrations").with(account("jugador")).with(csrf()))
                .andExpect(status().isCreated());
        mvc.perform(post(path + "/registrations").with(account("otro")).with(csrf()))
                .andExpect(status().isConflict());
        mvc.perform(delete(path + "/registrations/me").with(account("jugador")).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get(path)).andExpect(jsonPath("$.available").value(1));
    }

    private static RequestPostProcessor account(String name) {
        return oidcLogin().idToken(token -> token.subject(name).issuer("https://identity.api.test")
                .claim("name", name).claim("email", name + "@api.test").claim("email_verified", true));
    }
}
