package com.nexus.core.auth;

import com.nexus.core.auth.dto.AuthResponse;
import com.nexus.core.auth.dto.LoginRequest;
import com.nexus.core.auth.dto.RegisterRequest;

public interface AuthService {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);
}
