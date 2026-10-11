package com.srmecotech.ecoride.admin;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/auth")
@Tag(name = "Admin · account")
public class AdminAuthController {

    private final AdminCommandService commands;

    public AdminAuthController(AdminCommandService commands) {
        this.commands = commands;
    }

    @PostMapping("/login")
    public AdminDtos.AuthResponse login(@Valid @RequestBody AdminDtos.LoginRequest request) {
        return commands.login(request);
    }
}
