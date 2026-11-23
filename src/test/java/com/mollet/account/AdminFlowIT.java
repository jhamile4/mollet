package com.mollet.account;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mollet.IntegrationTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class AdminFlowIT extends IntegrationTest {

    private String adminToken() throws Exception {
        // Creado al arrancar por AdminBootstrap con las credenciales de application-test.yml
        return "Bearer " + login("admin@mollet.test", "AdminTest123");
    }

    @Test
    void regularUserCannotAccessAdminEndpoints() throws Exception {
        Session user = registerUser();

        mvc.perform(get("/api/v1/admin/accounts").header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void adminCanListAccountsWithPagination() throws Exception {
        registerUser();

        mvc.perform(get("/api/v1/admin/accounts?status=ACTIVE&size=1").header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].ownerEmail").exists());
    }

    @Test
    void blockedAccountCannotSendOrReceiveTransfers() throws Exception {
        Session ana = registerUser();
        Session luis = registerUser();
        deposit(ana, "50.00");
        String admin = adminToken();

        mvc.perform(patch("/api/v1/admin/accounts/{n}/status", luis.accountNumber())
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "BLOCKED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"));

        mvc.perform(post("/api/v1/transfers")
                        .header(HttpHeaders.AUTHORIZATION, ana.bearer())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("targetAccountNumber", luis.accountNumber(), "amount", "5.00"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ACCOUNT_BLOCKED"));
    }

    @Test
    void changingStatusOfUnknownAccountReturns404() throws Exception {
        mvc.perform(patch("/api/v1/admin/accounts/{n}/status", "00000000000000")
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "BLOCKED"))))
                .andExpect(status().isNotFound());
    }
}
