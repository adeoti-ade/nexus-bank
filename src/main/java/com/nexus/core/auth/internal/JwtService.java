package com.nexus.core.auth.internal;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Service
class JwtService {

    private final SecretKey signingKey;
    private final JwtProperties properties;

    JwtService(JwtProperties properties) {
        this.properties = properties;
        if (properties.secret() == null || properties.secret().isBlank()) {
            throw new IllegalStateException("JWT_SECRET environment variable must be set — no default is allowed");
        }
        this.signingKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(properties.secret()));
    }

    String generateToken(UserDetails userDetails) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + properties.expirationMs());

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userDetails.getUsername())
                .issuer(properties.issuer())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    String extractUsername(String token) {
        return extractClaims(token).getSubject();
    }

    String extractJti(String token) {
        return extractClaims(token).getId();
    }

    Date extractExpiration(String token) {
        return extractClaims(token).getExpiration();
    }

    boolean isTokenValid(String token, UserDetails userDetails) {
        String username = extractUsername(token);
        return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return extractClaims(token).getExpiration().before(new Date());
    }

    private Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
