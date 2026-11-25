package com.webhook.Webhook.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.webhook.Webhook.entity.DeliveryAttempt;

public interface DeliveryAttemptRepo extends MongoRepository<DeliveryAttempt, String> {

    List<DeliveryAttempt> findByJobIdOrderByAttemptNumberAsc(String jobId);
}
