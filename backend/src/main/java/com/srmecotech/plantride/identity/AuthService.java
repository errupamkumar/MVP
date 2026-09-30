package com.srmecotech.plantride.identity;

import com.srmecotech.plantride.common.error.NotFoundException;
import com.srmecotech.plantride.common.security.JwtService;
import com.srmecotech.plantride.identity.dto.AuthDtos.LoginRequest;
import com.srmecotech.plantride.identity.dto.AuthDtos.LoginResponse;
import com.srmecotech.plantride.identity.dto.AuthDtos.UserSummary;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    /** Same message for unknown user and wrong password: do not reveal which accounts exist. */
    private static final String BAD_CREDENTIALS = "That ID and password do not match.";
    private static final String TIMING_DUMMY_HASH = "$2b$10$ca9PWoYWONfy9qWjGCXOFOq1AY9RfyePexcvG.EzOVGKeeyHItw1y";

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(AppUserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        AppUser user = userRepository.findByUsername(request.username().trim()).orElse(null);
        if (user == null) {
            // Spend the same BCrypt time as a real check so response timing does not reveal valid IDs.
            passwordEncoder.matches(request.password(), TIMING_DUMMY_HASH);
            throw new BadCredentialsException(BAD_CREDENTIALS);
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException(BAD_CREDENTIALS);
        }
        if (!user.isActive()) {
            throw new DisabledException("This account is disabled. Contact the transport desk.");
        }
        JwtService.IssuedToken issued = jwtService.issue(user);
        return new LoginResponse(issued.token(), issued.expiresAt(), UserSummary.of(user));
    }

    @Transactional(readOnly = true)
    public UserSummary me(Long userId) {
        return userRepository.findById(userId)
                .map(UserSummary::of)
                .orElseThrow(() -> new NotFoundException("User", userId));
    }
}
