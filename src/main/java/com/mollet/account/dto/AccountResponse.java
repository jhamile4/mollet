package com.mollet.account.dto;

import com.mollet.account.AccountStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record AccountResponse(
        String accountNumber,
        String currency,
        BigDecimal balance,
        BigDecimal dailyLimit,
        AccountStatus status,
        Instant createdAt) {
}
