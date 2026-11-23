package com.mollet.movement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mollet.IntegrationTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class TransferFlowIT extends IntegrationTest {

    private ResultActions transfer(Session from, String toAccount, String amount, String key) throws Exception {
        return mvc.perform(post("/api/v1/transfers")
                .header(HttpHeaders.AUTHORIZATION, from.bearer())
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("targetAccountNumber", toAccount, "amount", amount, "description", "Prueba"))));
    }

    @Test
    void fullTransferFlowUpdatesBalancesAndBothHistories() throws Exception {
        Session ana = registerUser();
        Session luis = registerUser();
        deposit(ana, "200.00");

        transfer(ana, luis.accountNumber(), "75.25", UUID.randomUUID().toString())
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "false"))
                .andExpect(jsonPath("$.direction").value("OUT"))
                .andExpect(jsonPath("$.counterpartAccount").value(luis.accountNumber()));

        assertThat(myAccount(ana).get("balance").decimalValue()).isEqualByComparingTo("124.75");
        assertThat(myAccount(luis).get("balance").decimalValue()).isEqualByComparingTo("75.25");

        mvc.perform(get("/api/v1/accounts/me/movements").header(HttpHeaders.AUTHORIZATION, ana.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].type").value("TRANSFER"))   // más reciente primero
                .andExpect(jsonPath("$.content[0].direction").value("OUT"))
                .andExpect(jsonPath("$.content[1].type").value("DEPOSIT"));

        mvc.perform(get("/api/v1/accounts/me/movements").header(HttpHeaders.AUTHORIZATION, luis.bearer()))
                .andExpect(jsonPath("$.content[0].direction").value("IN"))
                .andExpect(jsonPath("$.content[0].counterpartAccount").value(ana.accountNumber()));
    }

    @Test
    void retryingWithSameIdempotencyKeyDoesNotChargeTwice() throws Exception {
        Session ana = registerUser();
        Session luis = registerUser();
        deposit(ana, "100.00");
        String key = UUID.randomUUID().toString();

        String first = transfer(ana, luis.accountNumber(), "30.00", key)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String second = transfer(ana, luis.accountNumber(), "30.00", key)
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andReturn().getResponse().getContentAsString();

        assertThat(read(second).get("id")).isEqualTo(read(first).get("id"));
        assertThat(myAccount(ana).get("balance").decimalValue()).isEqualByComparingTo("70.00");
        assertThat(myAccount(luis).get("balance").decimalValue()).isEqualByComparingTo("30.00");
    }

    @Test
    void insufficientFundsReturns422AndChangesNothing() throws Exception {
        Session ana = registerUser();
        Session luis = registerUser();
        deposit(ana, "10.00");

        transfer(ana, luis.accountNumber(), "10.01", UUID.randomUUID().toString())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"))
                .andExpect(jsonPath("$.instance").value("/api/v1/transfers"));

        assertThat(myAccount(ana).get("balance").decimalValue()).isEqualByComparingTo("10.00");
    }

    @Test
    void dailyLimitIsEnforced() throws Exception {
        Session ana = registerUser();
        Session luis = registerUser();
        deposit(ana, "1000.00");

        transfer(ana, luis.accountNumber(), "400.00", UUID.randomUUID().toString()).andExpect(status().isCreated());
        transfer(ana, luis.accountNumber(), "100.01", UUID.randomUUID().toString())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DAILY_LIMIT_EXCEEDED"));
        transfer(ana, luis.accountNumber(), "100.00", UUID.randomUUID().toString()).andExpect(status().isCreated());
    }

    @Test
    void missingIdempotencyKeyReturns400() throws Exception {
        Session ana = registerUser();

        mvc.perform(post("/api/v1/transfers")
                        .header(HttpHeaders.AUTHORIZATION, ana.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("targetAccountNumber", "12345678901234", "amount", "1.00"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void unknownTargetAccountReturns404() throws Exception {
        Session ana = registerUser();
        deposit(ana, "10.00");

        transfer(ana, "99999999999999", "1.00", UUID.randomUUID().toString())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    void amountWithMoreThanTwoDecimalsIsRejected() throws Exception {
        Session ana = registerUser();
        Session luis = registerUser();

        transfer(ana, luis.accountNumber(), "1.001", UUID.randomUUID().toString())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("amount"));
    }

    @Test
    void historyIsPaginated() throws Exception {
        Session ana = registerUser();
        for (int i = 0; i < 3; i++) {
            deposit(ana, "1.00");
        }

        mvc.perform(get("/api/v1/accounts/me/movements?page=0&size=2").header(HttpHeaders.AUTHORIZATION, ana.bearer()))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.last").value(false));
    }
}
