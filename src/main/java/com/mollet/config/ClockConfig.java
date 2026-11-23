package com.mollet.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Inyectamos un Clock en vez de llamar a Instant.now() directamente:
 * en las pruebas podemos "congelar" la hora y probar el límite diario sin esperar un día.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
