package com.mollet.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger con botón "Authorize": pegas el token del login y pruebas los endpoints protegidos. */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI molletOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Mollet API")
                        .version("v1")
                        .description("""
                                Billetera digital en soles: registro, cuentas, recargas, transferencias \
                                con idempotencia y límite diario, historial paginado y administración.

                                **Cómo probar:** 1) `POST /api/v1/auth/register` 2) copia el `accessToken` \
                                3) botón **Authorize** 4) usa los demás endpoints."""))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
