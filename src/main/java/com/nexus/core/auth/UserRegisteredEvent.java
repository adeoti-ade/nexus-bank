package com.nexus.core.auth;

import java.util.UUID;

public record UserRegisteredEvent(UUID userId, String email) {
}
