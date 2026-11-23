package com.mollet.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Propiedades "mollet.*" de application.yml, tipadas y validadas al arrancar.
 * Si falta algo obligatorio (por ejemplo JWT_SECRET en producción) la app no levanta,
 * en vez de fallar más tarde con un error raro.
 */
@Validated
@ConfigurationProperties("mollet")
public record MolletProperties(
        @NotBlank String timezone,
        @Valid @NotNull Jwt jwt,
        @Valid @NotNull Transfer transfer,
        Admin admin) {

    public ZoneId zone() {
        return ZoneId.of(timezone);
    }

    public record Jwt(
            @NotBlank
            @Size(min = 32, message = "debe tener al menos 32 caracteres para HS256")
            String secret,
            @NotNull Duration expiration,
            @NotBlank String issuer) {
    }

    public record Transfer(
            @NotNull @DecimalMin("0.01") BigDecimal dailyLimit,
            @NotNull @DecimalMin("0.01") BigDecimal maxDeposit) {
    }

    public record Admin(String email, String password) {
    }
}
