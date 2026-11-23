package com.mollet.account;

import com.mollet.account.dto.AccountResponse;
import com.mollet.account.dto.DepositRequest;
import com.mollet.common.web.PageResponse;
import com.mollet.movement.dto.MovementResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Todo es "/me": el usuario se obtiene del token, nunca de un id en la URL.
 * Así nadie puede ver la cuenta de otro cambiando un número en la ruta.
 */
@Tag(name = "2. Mi cuenta")
@RestController
@RequestMapping("/api/v1/accounts/me")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @Operation(summary = "Ver mi cuenta y saldo")
    @GetMapping
    public AccountResponse myAccount(@AuthenticationPrincipal Jwt jwt) {
        return accountService.getMyAccount(userId(jwt));
    }

    @Operation(summary = "Recargar saldo (simulado)")
    @PostMapping("/deposits")
    @ResponseStatus(HttpStatus.CREATED)
    public MovementResponse deposit(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DepositRequest request) {
        return accountService.deposit(userId(jwt), request);
    }

    @Operation(summary = "Historial de movimientos (paginado, más recientes primero)")
    @GetMapping("/movements")
    public PageResponse<MovementResponse> movements(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return accountService.history(userId(jwt), page, size);
    }

    static Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
