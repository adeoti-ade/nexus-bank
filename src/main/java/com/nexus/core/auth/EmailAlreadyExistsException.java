package com.nexus.core.auth;

public class EmailAlreadyExistsException extends RuntimeException {

    public EmailAlreadyExistsException(String email) {
        super("A user with email '%s' already exists".formatted(email));
    }
}
