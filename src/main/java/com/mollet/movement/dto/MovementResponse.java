package com.mollet.movement.dto;

import com.mollet.movement.MovementType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;

/** Un movimiento visto desde una cuenta: IN si entró dinero, OUT si salió. */
public record MovementResponse(
        Long id,
        MovementType type,
        Direction direction,
        BigDecimal amount,
        @Schema(description = "Cuenta de la otra parte; vacío en recargas")
        String counterpartAccount,
        String description,
        Instant createdAt) {

    public enum Direction {
        IN,
        OUT
    }
}
