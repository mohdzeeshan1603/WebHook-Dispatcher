package com.webhook.Webhook.repository;

import com.webhook.Webhook.entity.DeliveryAttempt;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Repository
public class DeliveryAttemptRepo {

    private final Map<String, DeliveryAttempt> attempts = new ConcurrentHashMap<>();

    public DeliveryAttempt save(DeliveryAttempt attempt) {
        if (attempt.getId() == null || attempt.getId().isBlank()) {
            attempt.setId(UUID.randomUUID().toString());
        }
        attempts.put(attempt.getId(), attempt);
        return attempt;
    }

    public Optional<DeliveryAttempt> findById(String id) {
        return Optional.ofNullable(attempts.get(id));
    }

    public List<DeliveryAttempt> findAll() {
        return List.copyOf(attempts.values());
    }

    // Equivalent to: findByJobIdOrderByAttemptNumberAsc
    public List<DeliveryAttempt> findByJobIdOrderByAttemptNumberAsc(String jobId) {
        return attempts.values().stream()
                .filter(a -> jobId != null && jobId.equals(a.getJobId()))
                .sorted(Comparator.comparingInt(DeliveryAttempt::getAttemptNumber))
                .collect(Collectors.toList());
    }
}