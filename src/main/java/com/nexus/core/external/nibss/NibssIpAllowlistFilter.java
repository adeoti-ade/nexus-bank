package com.nexus.core.external.nibss;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

class NibssIpAllowlistFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(NibssIpAllowlistFilter.class);

    private final List<String> allowedIps;

    NibssIpAllowlistFilter(List<String> allowedIps) {
        this.allowedIps = allowedIps;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String clientIp = resolveClientIp(request);

        // If allowlist is empty or contains "*", allow all (useful for dev/mock mode)
        if (!allowedIps.isEmpty() && !allowedIps.contains("*") && !allowedIps.contains(clientIp)) {
            log.warn("NIBSS webhook request denied from unauthorized IP: {}", clientIp);
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied");
            return;
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/webhooks/nibss/");
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            // Take the first (original client) IP from the chain
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
