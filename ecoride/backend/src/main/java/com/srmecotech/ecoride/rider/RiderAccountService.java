package com.srmecotech.ecoride.rider;

import com.srmecotech.ecoride.common.error.BusinessRuleException;
import com.srmecotech.ecoride.common.error.InvalidRequestException;
import com.srmecotech.ecoride.common.error.NotFoundException;
import com.srmecotech.ecoride.common.security.AuthErrors;
import com.srmecotech.ecoride.common.security.JwtService;
import com.srmecotech.ecoride.common.security.Role;
import com.srmecotech.ecoride.common.util.Money;
import com.srmecotech.ecoride.common.util.Phones;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/** Rider sign-up, sign-in and profile. Rider accounts are separate from driver and admin accounts. */
@Service
public class RiderAccountService {

    /** New riders start with this in their wallet (demo: so wallet payments can be tried at once). */
    public static final BigDecimal WELCOME_CREDIT = Money.of(200);

    private final RiderRepository riders;
    private final SavedPlaceRepository places;
    private final WalletService wallet;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwt;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public RiderAccountService(RiderRepository riders, SavedPlaceRepository places, WalletService wallet,
                               PasswordEncoder passwordEncoder, JwtService jwt, JdbcTemplate jdbc, Clock clock) {
        this.riders = riders;
        this.places = places;
        this.wallet = wallet;
        this.passwordEncoder = passwordEncoder;
        this.jwt = jwt;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public RiderDtos.AuthResponse signup(RiderDtos.SignupRequest request) {
        String phone = Phones.normalise(request.phone());
        if (!phone.matches("[6-9]\\d{9}")) {
            throw new InvalidRequestException("PHONE_INVALID", "Enter a 10-digit Indian mobile number.");
        }
        String email = blankToNull(request.email());
        if (riders.existsByPhone(phone)) {
            throw new BusinessRuleException("PHONE_TAKEN", "An account with this mobile number already exists. Sign in instead.");
        }
        if (email != null && riders.existsByEmailIgnoreCase(email)) {
            throw new BusinessRuleException("EMAIL_TAKEN", "An account with this email already exists. Sign in instead.");
        }
        Instant now = clock.instant();
        Rider rider = new Rider();
        rider.setName(request.name().trim());
        rider.setPhone(phone);
        rider.setEmail(email);
        rider.setPasswordHash(passwordEncoder.encode(request.password()));
        rider.setStatus(RiderStatus.ACTIVE);
        rider.setRatingAvg(new BigDecimal("5.00"));
        rider.setRatingCount(0);
        rider.setWalletBalance(Money.ZERO);
        rider.setCreatedAt(now);
        rider.setLastLoginAt(now);
        riders.saveAndFlush(rider);
        wallet.credit(rider.getId(), null, "TOPUP", WELCOME_CREDIT, "Welcome credit");
        return issue(riders.findById(rider.getId()).orElseThrow());
    }

    @Transactional
    public RiderDtos.AuthResponse login(RiderDtos.LoginRequest request) {
        String login = request.login().trim();
        Rider rider = (Phones.looksLikePhone(login)
                ? riders.findByPhone(Phones.normalise(login))
                : riders.findByEmailIgnoreCase(login))
                .orElseThrow(AuthErrors::badCredentials);
        if (!passwordEncoder.matches(request.password(), rider.getPasswordHash())) {
            throw AuthErrors.badCredentials();
        }
        if (rider.getStatus() == RiderStatus.BLOCKED) {
            throw AuthErrors.blocked("This account is blocked" + (rider.getStatusReason() == null ? "." : ": " + rider.getStatusReason())
                    + " Contact EcoRide support.");
        }
        rider.setLastLoginAt(clock.instant());
        return issue(rider);
    }

    @Transactional(readOnly = true)
    public RiderDtos.Profile profile(long riderId) {
        return toProfile(riders.findById(riderId).orElseThrow(() -> new NotFoundException("Rider", riderId)));
    }

    @Transactional
    public RiderDtos.Profile update(long riderId, RiderDtos.UpdateProfileRequest request) {
        Rider rider = riders.findById(riderId).orElseThrow(() -> new NotFoundException("Rider", riderId));
        String email = blankToNull(request.email());
        if (email != null && !email.equalsIgnoreCase(rider.getEmail()) && riders.existsByEmailIgnoreCase(email)) {
            throw new BusinessRuleException("EMAIL_TAKEN", "Another account uses this email.");
        }
        rider.setName(request.name().trim());
        rider.setEmail(email);
        return toProfile(riders.save(rider));
    }

    public List<RiderDtos.SavedPlaceDto> places(long riderId) {
        return places.findByRiderIdOrderByLabelAscIdAsc(riderId).stream().map(RiderAccountService::toDto).toList();
    }

    /** Home and Work are one each: saving again replaces the old one. */
    @Transactional
    public RiderDtos.SavedPlaceDto savePlace(long riderId, RiderDtos.SavePlaceRequest request) {
        SavedPlace place = "OTHER".equals(request.label()) ? new SavedPlace()
                : places.findFirstByRiderIdAndLabel(riderId, request.label()).orElseGet(SavedPlace::new);
        place.setRiderId(riderId);
        place.setLabel(request.label());
        place.setName(request.name().trim());
        place.setAddress(request.address() == null || request.address().isBlank() ? request.name().trim() : request.address().trim());
        place.setLat(request.lat());
        place.setLng(request.lng());
        if (place.getCreatedAt() == null) {
            place.setCreatedAt(clock.instant());
        }
        return toDto(places.save(place));
    }

    @Transactional
    public void deletePlace(long riderId, long placeId) {
        places.findById(placeId).filter(p -> p.getRiderId() == riderId).ifPresent(places::delete);
    }

    public RiderDtos.Wallet wallet(long riderId) {
        return new RiderDtos.Wallet(wallet.balance(riderId), wallet.statement(riderId, 30));
    }

    /** Simulated top-up (a real app would open a UPI or card payment here). */
    @Transactional
    public RiderDtos.Wallet topUp(long riderId, BigDecimal amount) {
        wallet.credit(riderId, null, "TOPUP", amount, "Added via UPI (simulated)");
        return wallet(riderId);
    }

    private RiderDtos.AuthResponse issue(Rider rider) {
        JwtService.IssuedToken token = jwt.issue(rider.getId(), Role.RIDER, rider.getName());
        return new RiderDtos.AuthResponse(token.token(), token.expiresAt(), toProfile(rider));
    }

    private RiderDtos.Profile toProfile(Rider rider) {
        Long completed = jdbc.queryForObject("SELECT COUNT(*) FROM ride WHERE rider_id = ? AND status = 'COMPLETED'",
                Long.class, rider.getId());
        return new RiderDtos.Profile(rider.getId(), rider.getName(), rider.getPhone(), rider.getEmail(),
                rider.getRatingAvg(), rider.getRatingCount(), wallet.balance(rider.getId()),
                completed == null ? 0 : completed, rider.getCreatedAt());
    }

    private static RiderDtos.SavedPlaceDto toDto(SavedPlace p) {
        return new RiderDtos.SavedPlaceDto(p.getId(), p.getLabel(), p.getName(), p.getAddress(), p.getLat(), p.getLng());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }
}
