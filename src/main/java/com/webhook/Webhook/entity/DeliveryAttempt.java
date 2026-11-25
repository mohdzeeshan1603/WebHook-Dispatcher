package com.webhook.Webhook.entity;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "delivery_attempts")
public class DeliveryAttempt {
    @Id
    private String id;

    @Indexed
    private String jobId;

    private int attemptNumber;
    private AttemptStatus status;
    private Integer httpStatusCode;
    private String errorMessage;
    private long durationMs;
    @Indexed
    private Instant attemptedAt;
}
