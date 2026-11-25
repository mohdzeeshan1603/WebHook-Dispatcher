package com.webhook.Webhook.repository;

import com.webhook.Webhook.entity.Status;
import com.webhook.Webhook.entity.WebHook;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Repository
public class WebHookRepo {

    private final Map<String, WebHook> webhooks = new ConcurrentHashMap<>();

    public WebHook save(WebHook webhook) {
        if (webhook.getId() == null || webhook.getId().isBlank()) {
            webhook.setId(UUID.randomUUID().toString());
        }
        webhooks.put(webhook.getId(), webhook);
        return webhook;
    }

    public Optional<WebHook> findById(String id) {
        return Optional.ofNullable(webhooks.get(id));
    }

    public List<WebHook> findAll() {
        return List.copyOf(webhooks.values());
    }

    public void deleteById(String id) {
        webhooks.remove(id);
    }

    // Equivalent to: findTop20ByStatusInAndNextRunAtLessThanEqualOrderByNextRunAtAsc
    public List<WebHook> findTop20ByStatusInAndNextRunAtLessThanEqualOrderByNextRunAtAsc(
            List<Status> statuses,
            Instant now) {
        return webhooks.values().stream()
                .filter(w -> w.getStatus() != null && statuses.contains(w.getStatus()))
                .filter(w -> w.getNextRunAt() != null && !w.getNextRunAt().isAfter(now))
                .sorted(Comparator.comparing(WebHook::getNextRunAt))
                .limit(20)
                .collect(Collectors.toList());
    }

    // Equivalent to: findByStatusAndUpdatedAtLessThanEqual
    public List<WebHook> findByStatusAndUpdatedAtLessThanEqual(
            Status status,
            Instant cutoff) {
        return webhooks.values().stream()
                .filter(w -> status != null && status.equals(w.getStatus()))
                .filter(w -> w.getUpdatedAt() != null && !w.getUpdatedAt().isAfter(cutoff))
                .collect(Collectors.toList());
    }

    // Equivalent to: findByEventId
    public List<WebHook> findByEventId(String eventId) {
        return webhooks.values().stream()
                .filter(w -> eventId != null && eventId.equals(w.getEventId()))
                .collect(Collectors.toList());
    }
}