package com.adoptimizer.security;

import com.adoptimizer.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory brute-force protection: temporarily locks an email after repeated failed sign-ins. */
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private record Attempts(int failures, Instant lockedUntil) {
    }

    private final AppProperties properties;
    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    /** Remaining lock time, or {@link Duration#ZERO} when the email may try to sign in. */
    public Duration remainingLock(String email) {
        Attempts current = attempts.get(key(email));
        if (current == null || current.lockedUntil() == null) {
            return Duration.ZERO;
        }
        Duration remaining = Duration.between(Instant.now(), current.lockedUntil());
        if (remaining.isNegative() || remaining.isZero()) {
            attempts.remove(key(email));
            return Duration.ZERO;
        }
        return remaining;
    }

    public void recordFailure(String email) {
        int max = properties.getSecurity().getMaxLoginAttempts();
        Duration lockout = Duration.ofMinutes(properties.getSecurity().getLockoutMinutes());
        attempts.compute(key(email), (k, current) -> {
            int failures = (current == null ? 0 : current.failures()) + 1;
            Instant lockedUntil = failures >= max ? Instant.now().plus(lockout) : null;
            return new Attempts(failures, lockedUntil);
        });
    }

    public void recordSuccess(String email) {
        attempts.remove(key(email));
    }

    private static String key(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
