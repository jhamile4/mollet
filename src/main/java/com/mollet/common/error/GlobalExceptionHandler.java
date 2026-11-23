package com.mollet.common.error;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Manejo global de errores. Extiende ResponseEntityExceptionHandler, que ya traduce los errores
 * típicos de Spring MVC (JSON mal formado, header faltante, 404, 405...) a ProblemDetail.
 * Aquí agregamos nuestros errores de negocio y unificamos el formato:
 *
 * { "type": "about:blank", "title": "...", "status": 422, "detail": "...",
 *   "instance": "/api/v1/transfers", "code": "INSUFFICIENT_FUNDS", "timestamp": "..." }
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProblemDetail> handleBusiness(BusinessException ex, HttpServletRequest request) {
        ErrorCode code = ex.getCode();
        ProblemDetail body = ProblemDetails.of(code.status(), code.name(), code.title(), ex.getMessage(),
                request.getRequestURI());
        return ResponseEntity.status(code.status()).body(body);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleConflict(DataIntegrityViolationException ex,
            HttpServletRequest request) {
        // Ej.: dos registros simultáneos con el mismo email. La BD (UNIQUE) es la última barrera.
        log.warn("Violación de integridad: {}", ex.getMostSpecificCause().getMessage());
        ProblemDetail body = ProblemDetails.of(HttpStatus.CONFLICT, "CONFLICT", "Conflicto",
                "La operación entra en conflicto con datos existentes", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        ProblemDetail body = ProblemDetails.of(HttpStatus.FORBIDDEN, "FORBIDDEN", "Acceso denegado",
                "No tienes permisos para esta operación", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        // Nunca devolvemos el stacktrace al cliente: se registra en el log y respondemos genérico.
        log.error("Error inesperado en {}", request.getRequestURI(), ex);
        ProblemDetail body = ProblemDetails.of(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Error interno", "Ocurrió un error inesperado", request.getRequestURI());
        return ResponseEntity.internalServerError().body(body);
    }

    /** Errores de @Valid en el body: devolvemos la lista de campos inválidos. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> Map.of("field", e.getField(), "message", String.valueOf(e.getDefaultMessage())))
                .toList();
        ProblemDetail body = ex.getBody();
        body.setTitle("Datos inválidos");
        body.setDetail("Uno o más campos no son válidos");
        body.setProperty("code", "VALIDATION_ERROR");
        body.setProperty("errors", errors);
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    /**
     * Cuando un método tiene validaciones en parámetros (p. ej. el header Idempotency-Key), Spring
     * valida todo junto (header + body) y lanza esta excepción. Para errores del body usamos el
     * nombre del campo (FieldError); para el resto, el nombre del parámetro.
     */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = ex.getParameterValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream().map(err -> Map.of(
                        "field", err instanceof FieldError fe
                                ? fe.getField()
                                : String.valueOf(r.getMethodParameter().getParameterName()),
                        "message", String.valueOf(err.getDefaultMessage()))))
                .toList();
        ProblemDetail body = ex.getBody();
        body.setTitle("Datos inválidos");
        body.setDetail("Uno o más parámetros no son válidos");
        body.setProperty("code", "VALIDATION_ERROR");
        body.setProperty("errors", errors);
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    /**
     * Último paso por el que pasan todos los errores estándar de Spring (con el body ya armado):
     * les agregamos code/timestamp para que tengan el mismo formato que los nuestros.
     */
    @Override
    protected ResponseEntity<Object> createResponseEntity(@Nullable Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problem) {
            HttpStatus status = HttpStatus.resolve(statusCode.value());
            String path = request instanceof ServletWebRequest swr ? swr.getRequest().getRequestURI() : null;
            ProblemDetails.enrich(problem, status != null ? status.name() : "ERROR", path);
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }
}
