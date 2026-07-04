package com.hotel.authservice;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Auth-service ima spring-boot-starter-security na klasnom putu.
 * Bez ove konfiguracije Spring Boot podrazumevano zaklju\u010dava SVE endpointe
 * (HTTP Basic + CSRF), pa bi POST /auth/login vra\u0107ao 401/403.
 * Bezbednost je ionako na Gateway-u, pa ovde sve propu\u0161tamo.
 */
@Configuration
@EnableWebSecurity
public class AuthSecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
