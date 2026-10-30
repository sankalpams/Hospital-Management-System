package com.hms.security;

import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Component
public class LoginRateLimiter {
    private static final int MAX_ATTEMPTS = 10;
    private static final long WINDOW_SECONDS = 60;
    private final ConcurrentHashMap<String, ConcurrentLinkedDeque<Instant>> attempts = new ConcurrentHashMap<>();
    public boolean allow(String ip) {
        Instant cutoff = Instant.now().minusSeconds(WINDOW_SECONDS);
        ConcurrentLinkedDeque<Instant> history = attempts.computeIfAbsent(ip, ignored -> new ConcurrentLinkedDeque<>());
        while (history.peekFirst() != null && history.peekFirst().isBefore(cutoff)) history.pollFirst();
        if (history.size() >= MAX_ATTEMPTS) return false;
        history.addLast(Instant.now()); return true;
    }
}
