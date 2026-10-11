package com.srmecotech.ecoride.driver;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/driver/auth")
@Tag(name = "Driver app · account")
public class DriverAuthController {

    private final DriverAccountService accounts;

    public DriverAuthController(DriverAccountService accounts) {
        this.accounts = accounts;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Apply to drive: the account can sign in at once and go online after admin approval")
    public DriverDtos.AuthResponse signup(@Valid @RequestBody DriverDtos.SignupRequest request) {
        return accounts.signup(request);
    }

    @PostMapping("/login")
    @Operation(summary = "Sign in with mobile number or email and password")
    public DriverDtos.AuthResponse login(@Valid @RequestBody DriverDtos.LoginRequest request) {
        return accounts.login(request);
    }
}
