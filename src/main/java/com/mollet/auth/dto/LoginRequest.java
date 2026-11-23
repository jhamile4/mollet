package com.mollet.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @Schema(example = "ana@correo.com") @NotBlank @Email String email,
        @Schema(example = "ClaveSegura123") @NotBlank String password) {
}
