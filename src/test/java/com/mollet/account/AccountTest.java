package com.mollet.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mollet.TestData;
import com.mollet.common.error.BusinessException;
import com.mollet.common.error.ErrorCode;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AccountTest {

    @Test
    void debitSubtractsBalance() {
        Account account = TestData.account(1, "10000000000001", "100.00");

        account.debit(new BigDecimal("40.50"), Instant.EPOCH);

        assertThat(account.getBalance()).isEqualByComparingTo("59.50");
    }

    @Test
    void debitWithInsufficientFundsFailsAndKeepsBalance() {
        Account account = TestData.account(1, "10000000000001", "10.00");

        assertThatThrownBy(() -> account.debit(new BigDecimal("10.01"), Instant.EPOCH))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.INSUFFICIENT_FUNDS);
        assertThat(account.getBalance()).isEqualByComparingTo("10.00");
    }

    @Test
    void nonPositiveAmountsAreRejected() {
        Account account = TestData.account(1, "10000000000001", "10.00");

        assertThatThrownBy(() -> account.credit(BigDecimal.ZERO, Instant.EPOCH))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> account.debit(new BigDecimal("-1"), Instant.EPOCH))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void newAccountStartsActiveWithZeroBalanceInSoles() {
        Account account = Account.open(TestData.user(1, "a@b.com"), "10000000000001", new BigDecimal("500"),
                Instant.EPOCH);

        assertThat(account.isActive()).isTrue();
        assertThat(account.getCurrency()).isEqualTo("PEN");
        assertThat(account.getBalance()).isEqualByComparingTo("0");
    }
}
