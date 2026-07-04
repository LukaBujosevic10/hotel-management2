package com.hotel.gateway.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtGrantedAuthoritiesConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Central authorisation point.
 *
 * The gateway is the only component that sees the caller's identity, so the
 * manager / receptionist split is enforced here:
 *
 *   MANAGER      -- everything, including changing the room inventory
 *   RECEPTIONIST -- guests, reservations and payments; rooms are read only
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Value("${security.jwt.secret}")
    private String jwtSecret;

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeExchange(exchange -> exchange
                // Always allow the browser's CORS pre-flight (OPTIONS) request
                .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // --- Public endpoints ---
                .pathMatchers("/auth/**", "/api/auth/**").permitAll()
                .pathMatchers("/actuator/**").permitAll()
                .pathMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**",
                              "/*/v3/api-docs/**", "/webjars/**").permitAll()

                // Public read access to the room catalogue (browse rooms without login)
                .pathMatchers(HttpMethod.GET, "/api/rooms/**").permitAll()

                // --- Room inventory is administration, not front-desk work ---
                .pathMatchers(HttpMethod.POST, "/api/rooms/**").hasRole("MANAGER")
                .pathMatchers(HttpMethod.PUT, "/api/rooms/**").hasRole("MANAGER")
                .pathMatchers(HttpMethod.PATCH, "/api/rooms/**").hasRole("MANAGER")
                .pathMatchers(HttpMethod.DELETE, "/api/rooms/**").hasRole("MANAGER")

                // Deleting guests or reservations is destructive -> manager only
                .pathMatchers(HttpMethod.DELETE, "/api/guests/**").hasRole("MANAGER")
                .pathMatchers(HttpMethod.DELETE, "/api/reservations/**").hasRole("MANAGER")

                // --- Everything else: any signed-in user (admin or receptionist) ---
                .anyExchange().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                    .jwtDecoder(jwtDecoder())
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    /**
     * Spring Security reads authorities from the "scope"/"scp" claim by default.
     * Our tokens carry them in "roles", already prefixed with ROLE_, so the
     * prefix is cleared to avoid ending up with ROLE_ROLE_MANAGER.
     */
    @Bean
    public ReactiveJwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("");

        ReactiveJwtAuthenticationConverter converter = new ReactiveJwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(
                new ReactiveJwtGrantedAuthoritiesConverterAdapter(authorities));
        return converter;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));   // any origin (dev)
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public ReactiveJwtDecoder jwtDecoder() {
        SecretKeySpec key = new SecretKeySpec(
                jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusReactiveJwtDecoder.withSecretKey(key).build();
    }
}
