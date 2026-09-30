package com.srmecotech.plantride.identity.dto;

import com.srmecotech.plantride.identity.AppUser;
import com.srmecotech.plantride.identity.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank(message = "Enter your P. No., driver ID or username")
            @Size(max = 50) String username,
            @NotBlank(message = "Enter your password")
            @Size(max = 100) String password) {
    }

    public record LoginResponse(String token, Instant expiresAt, UserSummary user) {
    }

    public record UserSummary(Long id, String username, String fullName, Role role, String email, String phone) {

        public static UserSummary of(AppUser user) {
            return new UserSummary(user.getId(), user.getUsername(), user.getFullName(), user.getRole(),
                    user.getEmail(), user.getPhone());
        }
    }
}
