package com.mollet.movement;

import com.mollet.movement.TransferService.TransferResult;
import com.mollet.movement.dto.MovementResponse;
import com.mollet.movement.dto.TransferRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "3. Transferencias")
@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

    static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @Operation(summary = "Transferir a otra cuenta Mollet",
            description = """
                    Requiere el header **Idempotency-Key** (por ejemplo un UUID generado por el cliente). \
                    Si la misma petición se repite con la misma clave (p. ej. por un reintento tras un corte \
                    de red), NO se cobra de nuevo: se devuelve la transferencia original con 200 y el header \
                    `Idempotent-Replayed: true`. La primera vez responde 201.""")
    @PostMapping
    public ResponseEntity<MovementResponse> transfer(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(example = "3f1c2a9e-7b4d-4e8a-9c1f-2d5e6a7b8c9d")
            @RequestHeader(IDEMPOTENCY_HEADER)
            @Size(min = 8, max = 64) @Pattern(regexp = "[A-Za-z0-9_-]+") String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {
        TransferResult result = transferService.transfer(Long.valueOf(jwt.getSubject()), idempotencyKey, request);
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .header("Idempotent-Replayed", String.valueOf(result.replayed()))
                .body(result.movement());
    }
}
