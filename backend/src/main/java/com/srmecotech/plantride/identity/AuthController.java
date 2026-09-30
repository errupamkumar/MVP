package com.srmecotech.plantride.identity;

import com.srmecotech.plantride.common.security.AuthenticatedUser;
import com.srmecotech.plantride.identity.dto.AuthDtos.LoginRequest;
import com.srmecotech.plantride.identity.dto.AuthDtos.LoginResponse;
import com.srmecotech.plantride.identity.dto.AuthDtos.UserSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "1. Auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Sign in with P. No. (rider), driver ID (driver) or username (desk/admin)")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    @Operation(summary = "The signed-in user")
    public UserSummary me(@AuthenticationPrincipal AuthenticatedUser user) {
        return authService.me(user.id());
    }
}
