package com.nexus.core.auth.internal;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

class TransactionRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TransactionRateLimitFilter.class);

    private final JwtService jwtService;
    private final int maxRequestsPerMinute;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    TransactionRateLimitFilter(JwtService jwtService, int maxRequestsPerMinute) {
        this.jwtService = jwtService;
        this.maxRequestsPerMinute = maxRequestsPerMinute;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            // No valid token — pass through and let JwtAuthenticationFilter handle the 401
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);
        String username;
        try {
            username = jwtService.extractUsername(token);
        } catch (Exception e) {
            // Invalid token — pass through and let JwtAuthenticationFilter handle it
            filterChain.doFilter(request, response);
            return;
        }

        Bucket bucket = buckets.computeIfAbsent(username, this::newBucket);
        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            log.warn("Transaction rate limit exceeded for user: {} on {}", username, request.getRequestURI());
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            response.setContentType("application/json");
            response.getWriter().write("{\"title\":\"Too Many Requests\",\"detail\":\"Rate limit exceeded. Please wait before trying again.\",\"status\":429}");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !uri.startsWith("/api/transactions/") && !uri.startsWith("/api/accounts/");
    }

    private Bucket newBucket(String key) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(maxRequestsPerMinute)
                .refillGreedy(maxRequestsPerMinute, Duration.ofMinutes(1))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }
}
