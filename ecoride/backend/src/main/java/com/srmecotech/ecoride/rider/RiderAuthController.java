package com.srmecotech.ecoride.rider;

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
@RequestMapping("/api/rider/auth")
@Tag(name = "Rider app · account")
public class RiderAuthController {

    private final RiderAccountService accounts;

    public RiderAuthController(RiderAccountService accounts) {
        this.accounts = accounts;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a rider account (signed in straight away, with a ₹200 welcome credit)")
    public RiderDtos.AuthResponse signup(@Valid @RequestBody RiderDtos.SignupRequest request) {
        return accounts.signup(request);
    }

    @PostMapping("/login")
    @Operation(summary = "Sign in with mobile number or email and password")
    public RiderDtos.AuthResponse login(@Valid @RequestBody RiderDtos.LoginRequest request) {
        return accounts.login(request);
    }
}
