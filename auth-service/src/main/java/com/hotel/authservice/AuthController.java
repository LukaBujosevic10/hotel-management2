package com.hotel.authservice;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@Tag(name = "Authentication", description = "Issues JWTs carrying the caller's roles")
public class AuthController {

    /**
     * Roles are the contract between this service and the gateway.
     *
     *  ROLE_MANAGER      -- full access, including the room inventory
     *  ROLE_RECEPTIONIST -- day to day work: guests, reservations, payments
     *  ROLE_USER         -- granted to everyone, marks "is authenticated"
     */
    private static final List<String> MANAGER_ROLES =
            List.of("ROLE_MANAGER", "ROLE_USER");
    private static final List<String> RECEPTIONIST_ROLES =
            List.of("ROLE_RECEPTIONIST", "ROLE_USER");

    private final JwtTokenService tokenService;

    public AuthController(JwtTokenService tokenService) {
        this.tokenService = tokenService;
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    @PostMapping("/login")
    @Operation(summary = "Exchange demo credentials for a signed JWT")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req) throws Exception {
        if ("manager".equals(req.username()) && "manager123".equals(req.password())) {
            return ResponseEntity.ok(session("manager", "MANAGER", MANAGER_ROLES));
        }
        if ("receptionist".equals(req.username()) && "recept123".equals(req.password())) {
            return ResponseEntity.ok(session("receptionist", "RECEPTIONIST", RECEPTIONIST_ROLES));
        }
        return ResponseEntity.status(401)
                .body(Map.of("message", "Wrong username or password. Please check and try again."));
    }

    private Map<String, Object> session(String username, String role, List<String> roles) throws Exception {
        return Map.of(
                "token", tokenService.generateToken(username, roles),
                "type", "Bearer",
                "username", username,
                "role", role,
                "roles", roles);
    }
}
