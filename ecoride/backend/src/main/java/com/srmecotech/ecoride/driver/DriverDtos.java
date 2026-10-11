package com.srmecotech.ecoride.driver;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class DriverDtos {

    private DriverDtos() {
    }

    /** Driver onboarding: personal details, driving licence and the vehicle. */
    public record SignupRequest(
            @NotBlank(message = "Enter your name") @Size(min = 2, max = 80) String name,
            @NotBlank(message = "Enter your mobile number") String phone,
            @Email(message = "Enter a valid email") @Size(max = 120) String email,
            @NotBlank(message = "Choose a password") @Size(min = 8, max = 64, message = "Use at least 8 characters") String password,
            @NotBlank(message = "Enter your driving licence number") @Size(min = 8, max = 20) String licenseNo,
            @NotBlank(message = "Choose a vehicle type") String vehicleType,
            @NotBlank(message = "Enter the vehicle make") @Size(max = 30) String vehicleMake,
            @NotBlank(message = "Enter the vehicle model") @Size(max = 30) String vehicleModel,
            @NotBlank(message = "Enter the vehicle colour") @Size(max = 20) String vehicleColor,
            @NotBlank(message = "Enter the registration number") @Size(max = 14) String vehiclePlate,
            @NotNull(message = "Enter the vehicle year") @Min(value = 2008, message = "Vehicles from 2008 or later only") @Max(2030) Integer vehicleYear) {
    }

    public record LoginRequest(@NotBlank(message = "Enter your mobile number or email") String login,
                               @NotBlank(message = "Enter your password") String password) {
    }

    public record Vehicle(String type, String typeName, String make, String model, String color, String plate, int year) {
    }

    /**
     * @param acceptanceRate share of offers accepted, 0-100 (null before the first offer)
     */
    public record Profile(long id, String name, String phone, String email, String licenseNo, DriverStatus status,
                          String statusReason, Availability availability, BigDecimal rating, int ratingCount,
                          Integer acceptanceRate, Vehicle vehicle, Instant memberSince, Instant approvedAt) {
    }

    public record AuthResponse(String token, Instant expiresAt, Profile profile) {
    }

    /**
     * @param balance what EcoRide owes the driver right now (next payout); negative = the driver owes cash commission
     */
    public record Earnings(Period today, Period week, BigDecimal balance, List<Day> days,
                           List<DriverLedgerService.LedgerLine> lines) {
    }

    /**
     * @param net      everything earned in the period (fare share, tips, fees, minus penalties)
     * @param cashKept cash collected from riders on cash rides
     */
    public record Period(BigDecimal net, BigDecimal fares, BigDecimal tips, BigDecimal penalties, BigDecimal cashKept,
                         int trips, double distanceKm, int onlineMinutes) {
    }

    public record Day(LocalDate date, BigDecimal net, int trips) {
    }
}
