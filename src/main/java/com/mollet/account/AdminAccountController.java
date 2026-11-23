package com.mollet.account;

import com.mollet.account.dto.AdminAccountResponse;
import com.mollet.account.dto.UpdateStatusRequest;
import com.mollet.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Solo rol ADMIN (regla en SecurityConfig para todo /api/v1/admin/**). */
@Tag(name = "4. Administración")
@RestController
@RequestMapping("/api/v1/admin/accounts")
public class AdminAccountController {

    private final AdminAccountService adminAccountService;

    public AdminAccountController(AdminAccountService adminAccountService) {
        this.adminAccountService = adminAccountService;
    }

    @Operation(summary = "Listar cuentas (filtro opcional por estado)")
    @GetMapping
    public PageResponse<AdminAccountResponse> list(
            @RequestParam(required = false) AccountStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return adminAccountService.list(status, page, size);
    }

    @Operation(summary = "Bloquear o desbloquear una cuenta")
    @PatchMapping("/{accountNumber}/status")
    public AdminAccountResponse changeStatus(@PathVariable String accountNumber,
            @Valid @RequestBody UpdateStatusRequest request) {
        return adminAccountService.changeStatus(accountNumber, request.status());
    }
}
