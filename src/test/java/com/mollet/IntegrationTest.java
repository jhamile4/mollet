package com.mollet;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base de las pruebas de integración: app completa (seguridad, validación, JPA, Flyway)
 * contra PostgreSQL real. Cada prueba crea usuarios con emails únicos, así no se pisan entre sí.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper objectMapper;

    protected record Session(String token, String accountNumber) {

        public String bearer() {
            return "Bearer " + token;
        }
    }

    protected Session registerUser() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@mollet.test";
        String body = json(Map.of("email", email, "password", "ClaveSegura123", "fullName", "Usuario Test"));
        JsonNode tokenJson = read(mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String token = tokenJson.get("accessToken").asText();
        JsonNode account = read(mvc.perform(get("/api/v1/accounts/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return new Session(token, account.get("accountNumber").asText());
    }

    protected String login(String email, String password) throws Exception {
        String body = json(Map.of("email", email, "password", password));
        return read(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    protected void deposit(Session session, String amount) throws Exception {
        mvc.perform(post("/api/v1/accounts/me/deposits")
                        .header(HttpHeaders.AUTHORIZATION, session.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("amount", amount))))
                .andExpect(status().isCreated());
    }

    protected JsonNode myAccount(Session session) throws Exception {
        return read(mvc.perform(get("/api/v1/accounts/me").header(HttpHeaders.AUTHORIZATION, session.bearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    protected String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    protected JsonNode read(String json) throws Exception {
        return objectMapper.readTree(json);
    }
}
