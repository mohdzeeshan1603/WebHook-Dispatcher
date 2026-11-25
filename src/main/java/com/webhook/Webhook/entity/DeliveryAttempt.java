package com.webhook.Webhook.entity;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DeliveryAttempt {
    private String id;
    private String jobId;
    private int attemptNumber;
    private AttemptStatus status;
    private Integer httpStatusCode;
    private String errorMessage;
    private long durationMs;
    private Instant attemptedAt;
}