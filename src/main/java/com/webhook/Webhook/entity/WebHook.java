package com.webhook.Webhook.entity;

import java.time.Instant;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "webhooks")
public class WebHook {
    @Id
    private String id; // jobId (UUID)
    @Indexed
    private String eventId;
    private String destinationUrl;
    @Indexed
    private String destinationDomain;
    private Map<String, Object> payload;
    private Status status;
    private int attemptCount;
    private Instant nextRunAt;
    private Instant createdAt;
    private Instant updatedAt;
}
