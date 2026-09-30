package com.srmecotech.plantride.common.security;

import com.srmecotech.plantride.common.config.AppProperties;
import com.srmecotech.plantride.identity.AppUser;
import com.srmecotech.plantride.identity.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private static final String ISSUER = "plant-ride";

    private final SecretKey key;
    private final long ttlHours;
    private final Clock clock;

    public JwtService(AppProperties properties, Clock clock) {
        String secret = properties.security().jwtSecret();
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("plantride.security.jwt-secret must be at least 32 bytes for HS256");
        }
        if (secret.startsWith("plant-ride-local-dev-secret")) {
            log.warn("Using the built-in development JWT secret. Set JWT_SECRET outside local development.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttlHours = properties.security().jwtTtlHours();
        this.clock = clock;
    }

    public IssuedToken issue(AppUser user) {
        Instant now = clock.instant();
        Instant expires = now.plus(ttlHours, ChronoUnit.HOURS);
        String token = Jwts.builder()
                .issuer(ISSUER)
                .subject(String.valueOf(user.getId()))
                .claim("username", user.getUsername())
                .claim("role", user.getRole().name())
                .claim("name", user.getFullName())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expires))
                .signWith(key)
                .compact();
        return new IssuedToken(token, expires);
    }

    /**
     * @throws JwtException if the token is malformed, tampered with or expired
     */
    public AuthenticatedUser parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(ISSUER)
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return new AuthenticatedUser(
                Long.valueOf(claims.getSubject()),
                claims.get("username", String.class),
                Role.valueOf(claims.get("role", String.class)),
                claims.get("name", String.class));
    }

    public record IssuedToken(String token, Instant expiresAt) {
    }
}
