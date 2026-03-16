package com.nexus.core.auth.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

@Service
class TokenBlocklistService {

    private static final Logger log = LoggerFactory.getLogger(TokenBlocklistService.class);

    // Maps JWT ID (jti) -> expiry instant. Once the expiry passes, the entry is safe to remove.
    private final ConcurrentHashMap<String, Instant> blocklist = new ConcurrentHashMap<>();

    void block(String jti, Date expiry) {
        blocklist.put(jti, expiry.toInstant());
        log.debug("Token {} added to blocklist, expires at {}", jti, expiry);
    }

    boolean isBlocked(String jti) {
        return blocklist.containsKey(jti);
    }

    // Clean up expired entries every hour to prevent unbounded memory growth
    @Scheduled(fixedDelay = 3_600_000)
    void evictExpiredTokens() {
        Instant now = Instant.now();
        int before = blocklist.size();
        blocklist.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
        int removed = before - blocklist.size();
        if (removed > 0) {
            log.debug("Token blocklist cleanup: removed {} expired entries", removed);
        }
    }
}
