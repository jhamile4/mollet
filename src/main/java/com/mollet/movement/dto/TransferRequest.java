package com.mollet.movement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record TransferRequest(
        @Schema(example = "12345678901234", description = "Número de cuenta Mollet del destinatario (14 dígitos)")
        @NotBlank @Pattern(regexp = "\\d{14}", message = "debe tener 14 dígitos")
        String targetAccountNumber,

        @Schema(example = "50.00")
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 10, fraction = 2)
        BigDecimal amount,

        @Schema(example = "Pago del almuerzo")
        @Size(max = 140)
        String description) {
}
