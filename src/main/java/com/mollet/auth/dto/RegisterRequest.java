package com.mollet.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @Schema(example = "ana@correo.com")
        @NotBlank @Email @Size(max = 120)
        String email,

        // BCrypt solo usa los primeros 72 bytes, por eso el máximo
        @Schema(example = "ClaveSegura123")
        @NotBlank @Size(min = 8, max = 72, message = "debe tener entre 8 y 72 caracteres")
        String password,

        @Schema(example = "Ana Torres")
        @NotBlank @Size(max = 120)
        String fullName) {
}
