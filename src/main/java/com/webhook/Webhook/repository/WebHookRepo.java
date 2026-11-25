package com.webhook.Webhook.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.webhook.Webhook.entity.Status;
import com.webhook.Webhook.entity.WebHook;

public interface WebHookRepo extends MongoRepository<WebHook, String> {
    List<WebHook> findTop20ByStatusInAndNextRunAtLessThanEqualOrderByNextRunAtAsc(
            List<Status> statuses,
            Instant now);

    List<WebHook> findByStatusAndUpdatedAtLessThanEqual(
            Status status,
            Instant cutoff);

    List<WebHook> findByEventId(String eventId);
}
