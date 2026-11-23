package com.mollet.account;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/** Genera números de cuenta de 14 dígitos. Está aislado en su propia clase para poder simularlo en pruebas. */
@Component
public class AccountNumberGenerator {

    private static final long MIN = 10_000_000_000_000L; // 14 dígitos, sin cero inicial
    private static final long RANGE = 90_000_000_000_000L;

    private final SecureRandom random = new SecureRandom();

    public String next() {
        long value = MIN + (long) (random.nextDouble() * RANGE);
        return Long.toString(value);
    }
}
