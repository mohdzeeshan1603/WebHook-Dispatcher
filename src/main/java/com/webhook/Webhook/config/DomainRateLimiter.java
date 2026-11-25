package com.webhook.Webhook.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DomainRateLimiter {
    private final int maxRequestsPerWindow;
    private final long windowMillis;
    private final ConcurrentHashMap<String, Deque<Long>> domainTimestamps = new ConcurrentHashMap<>();

    public DomainRateLimiter(
            @Value("${webhook.rate-limit.max-requests-per-window:5}") int maxRequestsPerWindow,
            @Value("${webhook.rate-limit.window-seconds:10}") long windowSeconds) {
        this.maxRequestsPerWindow = maxRequestsPerWindow;
        this.windowMillis = windowSeconds * 1000L;
    }

    public boolean allowRequest(String domain) {
        if (domain == null || domain.isBlank()) {
            return true;
        }
        long now = Instant.now().toEpochMilli();
        Deque<Long> timestamps = domainTimestamps.computeIfAbsent(domain.toLowerCase(), k -> new ArrayDeque<>());

        synchronized (timestamps) {
            while (!timestamps.isEmpty() && (now - timestamps.peekFirst()) >= windowMillis) {
                timestamps.pollFirst();
            }
            if (timestamps.size() < maxRequestsPerWindow) {
                timestamps.addLast(now);
                return true;
            }
            return false;
        }
    }
}
