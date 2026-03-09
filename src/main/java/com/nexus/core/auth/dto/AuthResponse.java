package com.nexus.core.auth.dto;

import java.util.UUID;

public record AuthResponse(
        UUID userId,
        String email,
        String firstName,
        String lastName,
        String role,
        String accessToken,
        String tokenType
) {
    public AuthResponse(UUID userId, String email, String firstName, String lastName, String role, String accessToken) {
        this(userId, email, firstName, lastName, role, accessToken, "Bearer");
    }
}
