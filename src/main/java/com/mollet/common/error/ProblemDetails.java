package com.mollet.common.error;

import java.net.URI;
import java.time.Instant;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/** Fábrica única de respuestas de error, para que TODAS tengan la misma forma. */
public final class ProblemDetails {

    private ProblemDetails() {
    }

    public static ProblemDetail of(HttpStatusCode status, String code, String title, String detail, String path) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return enrich(problem, code, path);
    }

    /** Agrega los campos extra que usamos en todas las respuestas: code, timestamp e instance. */
    public static ProblemDetail enrich(ProblemDetail problem, String code, String path) {
        if (problem.getProperties() == null || !problem.getProperties().containsKey("code")) {
            problem.setProperty("code", code);
        }
        problem.setProperty("timestamp", Instant.now());
        if (path != null && problem.getInstance() == null) {
            problem.setInstance(URI.create(path));
        }
        return problem;
    }
}
