package com.docspot.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

/**
 * Configures Swagger/OpenAPI UI with JWT Bearer authentication.
 * After login, copy the access token and click "Authorize" in Swagger UI
 * to test secured endpoints directly from the browser.
 *
 * Swagger UI → http://localhost:8080/swagger-ui.html
 * OpenAPI JSON → http://localhost:8080/api-docs
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title       = "DocSpot API",
                version     = "1.0",
                description = "Medical Appointment Booking System — " +
                        "Roles: ADMIN | DOCTOR | PATIENT. " +
                        "Login to get a JWT, then click Authorize to use protected endpoints.",
                contact     = @Contact(name = "DocSpot Team", email = "admin@docspot.com")
        ),
        servers = {
                @Server(url = "http://localhost:8080", description = "Local Dev Server")
        },
        security = @SecurityRequirement(name = "bearerAuth")  // apply JWT globally
)
@SecurityScheme(
        name         = "bearerAuth",
        description  = "Paste your JWT access token here (without 'Bearer ' prefix)",
        scheme       = "bearer",
        type         = SecuritySchemeType.HTTP,
        bearerFormat = "JWT",
        in           = SecuritySchemeIn.HEADER
)
public class OpenApiConfig {
    // No beans needed — all configured via annotations above
}