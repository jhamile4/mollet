package com.mollet.common.error;

import org.springframework.http.HttpStatus;

/**
 * Catálogo de errores de negocio. Cada error tiene un código estable (lo que lee el frontend)
 * y un status HTTP. 422 = la petición es válida, pero rompe una regla del negocio.
 */
public enum ErrorCode {
    EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "Email ya registrado"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Credenciales inválidas"),
    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "Cuenta no encontrada"),
    ACCOUNT_BLOCKED(HttpStatus.UNPROCESSABLE_ENTITY, "Cuenta bloqueada"),
    SAME_ACCOUNT_TRANSFER(HttpStatus.UNPROCESSABLE_ENTITY, "Transferencia a la misma cuenta"),
    INSUFFICIENT_FUNDS(HttpStatus.UNPROCESSABLE_ENTITY, "Saldo insuficiente"),
    DAILY_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY, "Límite diario excedido"),
    DEPOSIT_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY, "Monto de recarga excedido"),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT, "Clave de idempotencia reutilizada");

    private final HttpStatus status;
    private final String title;

    ErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }
}
