package com.petmatch.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import org.springframework.context.annotation.Configuration;

/**
 * Metadata for the generated API documentation (Swagger UI at /swagger-ui.html).
 * The "bearerAuth" scheme adds an Authorize button: paste the token returned by
 * /api/auth/login to try the protected endpoints from the browser.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "PetMatch API",
                version = "0.1.0",
                description = "Matches lost-pet reports with sightings. Exact locations are never "
                        + "returned, and contact details are shared only after both sides agree."),
        security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT")
public class OpenApiConfig {
}
