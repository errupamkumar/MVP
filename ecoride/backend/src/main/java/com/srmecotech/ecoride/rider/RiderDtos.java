package com.srmecotech.ecoride.rider;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class RiderDtos {

    private RiderDtos() {
    }

    public record SignupRequest(
            @NotBlank(message = "Enter your name") @Size(min = 2, max = 80) String name,
            @NotBlank(message = "Enter your mobile number") String phone,
            @Email(message = "Enter a valid email") @Size(max = 120) String email,
            @NotBlank(message = "Choose a password") @Size(min = 8, max = 64, message = "Use at least 8 characters") String password) {
    }

    /** @param login mobile number or email */
    public record LoginRequest(@NotBlank(message = "Enter your mobile number or email") String login,
                               @NotBlank(message = "Enter your password") String password) {
    }

    public record Profile(long id, String name, String phone, String email, BigDecimal rating, int ratingCount,
                          BigDecimal walletBalance, long completedRides, Instant memberSince) {
    }

    public record AuthResponse(String token, Instant expiresAt, Profile profile) {
    }

    public record UpdateProfileRequest(@NotBlank @Size(min = 2, max = 80) String name,
                                       @Email @Size(max = 120) String email) {
    }

    public record SavedPlaceDto(long id, String label, String name, String address, double lat, double lng) {
    }

    public record SavePlaceRequest(
            @NotBlank @Pattern(regexp = "HOME|WORK|OTHER", message = "Label must be HOME, WORK or OTHER") String label,
            @NotBlank @Size(max = 80) String name,
            @Size(max = 255) String address,
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double lng) {
    }

    public record Wallet(BigDecimal balance, List<WalletService.WalletLine> lines) {
    }

    public record TopUpRequest(
            @NotNull @DecimalMin(value = "10", message = "Add at least ₹10")
            @DecimalMax(value = "5000", message = "Add at most ₹5,000 at a time") BigDecimal amount) {
    }
}
