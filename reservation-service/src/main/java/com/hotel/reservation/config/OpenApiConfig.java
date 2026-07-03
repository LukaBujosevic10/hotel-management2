package com.hotel.reservation.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI reservationServiceOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("Reservation Service API")
                .version("1.0.0")
                .description("Hotel Management - Reservation microservice (Feign + Resilience4j + RabbitMQ)"));
    }
}
