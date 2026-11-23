package com.mollet.account.dto;

import com.mollet.account.AccountStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record AdminAccountResponse(
        Long id,
        String accountNumber,
        String ownerName,
        String ownerEmail,
        BigDecimal balance,
        AccountStatus status,
        Instant createdAt) {
}
