package com.nexus.core.auth;

public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(String email) {
        super("User with email '%s' not found".formatted(email));
    }
}
