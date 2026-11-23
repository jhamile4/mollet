package com.mollet.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mollet.IntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class AuthFlowIT extends IntegrationTest {

    @Test
    void registeredUserGetsAnAccountInSolesWithZeroBalance() throws Exception {
        Session session = registerUser();

        mvc.perform(get("/api/v1/accounts/me").header(HttpHeaders.AUTHORIZATION, session.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").value(session.accountNumber()))
                .andExpect(jsonPath("$.currency").value("PEN"))
                .andExpect(jsonPath("$.balance").value(0.0))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void duplicatedEmailReturns409WithErrorCode() throws Exception {
        String body = json(Map.of("email", "repetido@mollet.test", "password", "ClaveSegura123", "fullName", "X"));
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body));

        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void invalidBodyReturns400WithFieldErrors() throws Exception {
        String body = json(Map.of("email", "no-es-email", "password", "123", "fullName", ""));

        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.length()").value(3));
    }

    @Test
    void wrongPasswordReturns401() throws Exception {
        String body = json(Map.of("email", "nadie@mollet.test", "password", "ClaveIncorrecta"));

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void protectedEndpointWithoutTokenReturns401InSameFormat() throws Exception {
        mvc.perform(get("/api/v1/accounts/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void tamperedTokenIsRejected() throws Exception {
        Session session = registerUser();
        String tampered = session.token().substring(0, session.token().length() - 4) + "abcd";

        mvc.perform(get("/api/v1/accounts/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void healthAndSwaggerArePublic() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Mollet API"));
    }
}
