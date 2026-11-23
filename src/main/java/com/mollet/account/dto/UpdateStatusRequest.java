package com.mollet.account.dto;

import com.mollet.account.AccountStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record UpdateStatusRequest(@Schema(example = "BLOCKED") @NotNull AccountStatus status) {
}
