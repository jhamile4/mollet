package com.mollet.common.error;

/**
 * Excepción para reglas de negocio. Los servicios la lanzan y GlobalExceptionHandler
 * la convierte en una respuesta JSON consistente (ProblemDetail, RFC 9457).
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode code;

    public BusinessException(ErrorCode code, String detail) {
        super(detail);
        this.code = code;
    }

    public ErrorCode getCode() {
        return code;
    }
}
