package com.hotel.reservation;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(servers = { @Server(url = "/", description = "Lokalni server") })
public class SwaggerConfig {
    @Bean
    public OpenAPI customOpenAPI() {
        final String securitySchemeName = "bearerAuth";

        return new OpenAPI()
                // Forsira relativnu putanju (rešava tvoj prethodni Docker/Gateway problem)
                .servers(java.util.List.of(new io.swagger.v3.oas.models.servers.Server().url("/")))
                // 1. Dodaje globalni zahtev za sigurnošću na sve API rute
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                // 2. Definiše samu šemu (tip autentifikacije)
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")))
                .info(new Info().title("API Dokumentacija").version("1.0"));
    }
}