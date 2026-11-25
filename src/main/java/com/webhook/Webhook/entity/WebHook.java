package com.webhook.Webhook.entity;

import java.time.Instant;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebHook {
    private String id; // jobId (UUID)
    private String eventId;
    private String destinationUrl;
    private String destinationDomain;
    private Map<String, Object> payload;
    private Status status;
    private int attemptCount;
    private Instant nextRunAt;
    private Instant createdAt;
    private Instant updatedAt;
}