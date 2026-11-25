// package com.webhook.Webhook.config;

// import com.webhook.Webhook.entity.AttemptStatus;
// import com.webhook.Webhook.entity.DeliveryAttempt;
// import com.webhook.Webhook.entity.Status;
// import com.webhook.Webhook.entity.WebHook;
// import com.webhook.Webhook.repository.DeliveryAttemptRepo;
// import com.webhook.Webhook.repository.WebHookRepo;

// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;
// import org.springframework.beans.factory.annotation.Value;
// import org.springframework.http.HttpStatusCode;
// import org.springframework.http.MediaType;
// import org.springframework.http.ResponseEntity;
// import org.springframework.scheduling.annotation.Scheduled;
// import org.springframework.stereotype.Component;
// import org.springframework.web.reactive.function.client.WebClient;
// import org.springframework.web.reactive.function.client.WebClientResponseException;

// import java.time.Duration;
// import java.time.Instant;
// import java.util.List;

// @Component
// public class WebHookDispatcherWorker {

//     private static final Logger log = LoggerFactory.getLogger(WebHookDispatcherWorker.class);
//     private static final long[] BACKOFF_SECONDS = { 2L, 4L, 8L };

//     private final WebHookRepo webHookRepository;
//     private final DeliveryAttemptRepo deliveryAttemptRepository;
//     private final DomainRateLimiter domainRateLimiter;
//     private final WebClient webhookWebClient;
//     private final int maxRetries;
//     private final int responseTimeoutSeconds;

//     public WebHookDispatcherWorker(
//             WebHookRepo webHookRepository,
//             DeliveryAttemptRepo deliveryAttemptRepository,
//             DomainRateLimiter domainRateLimiter,
//             WebClient webhookWebClient,
//             @Value("${webhook.worker.max-retries:3}") int maxRetries,
//             @Value("${webhook.worker.response-timeout-seconds:5}") int responseTimeoutSeconds) {
//         this.webHookRepository = webHookRepository;
//         this.deliveryAttemptRepository = deliveryAttemptRepository;
//         this.domainRateLimiter = domainRateLimiter;
//         this.webhookWebClient = webhookWebClient;
//         this.maxRetries = maxRetries;
//         this.responseTimeoutSeconds = responseTimeoutSeconds;
//     }

//     @Scheduled(fixedDelayString = "${webhook.worker.poll-interval-ms:500}")
//     public void pollAndDispatch() {
//         Instant now = Instant.now();
//         List<WebHook> dueJobs = webHookRepository.findTop20ByStatusInAndNextRunAtLessThanEqualOrderByNextRunAtAsc(
//                 List.of(Status.QUEUED, Status.RETRYING),
//                 now);

//         for (WebHook job : dueJobs) {
//             processSingleJob(job);
//         }
//     }

//     private void processSingleJob(WebHook job) {
//         if (!domainRateLimiter.allowRequest(job.getDestinationDomain())) {
//             job.setNextRunAt(Instant.now().plusSeconds(2));
//             job.setUpdatedAt(Instant.now());
//             webHookRepository.save(job);
//             log.info("Rate limited domain={}, rescheduled jobId={}", job.getDestinationDomain(), job.getId());
//             return;
//         }

//         job.setStatus(Status.PROCESSING);
//         job.setUpdatedAt(Instant.now());
//         webHookRepository.save(job);

//         int currentAttempt = job.getAttemptCount() + 1;
//         job.setAttemptCount(currentAttempt);

//         long startTime = System.currentTimeMillis();
//         Instant attemptedAt = Instant.now();

//         try {
//             ResponseEntity<String> response = webhookWebClient.post()
//                     .uri(job.getDestinationUrl())
//                     .contentType(MediaType.APPLICATION_JSON)
//                     .bodyValue(job.getPayload())
//                     .retrieve()
//                     .toEntity(String.class)
//                     .timeout(Duration.ofSeconds(responseTimeoutSeconds + 1L))
//                     .block();

//             long durationMs = System.currentTimeMillis() - startTime;
//             int statusCode = response != null ? response.getStatusCode().value() : 200;

//             handleSuccess(job, currentAttempt, statusCode, durationMs, attemptedAt);

//         } catch (WebClientResponseException ex) {
//             long durationMs = System.currentTimeMillis() - startTime;
//             int statusCode = ex.getStatusCode().value();
//             handleHttpFailure(job, currentAttempt, ex.getStatusCode(), statusCode, ex.getMessage(), durationMs,
//                     attemptedAt);

//         } catch (Exception ex) {
//             long durationMs = System.currentTimeMillis() - startTime;
//             handleRetryOrFail(job, currentAttempt, null, "Timeout or network error: " + ex.getMessage(), durationMs,
//                     attemptedAt);
//         }
//     }

//     private void handleSuccess(WebHook job, int attemptNumber, int statusCode, long durationMs, Instant attemptedAt) {
//         job.setStatus(Status.COMPLETED);
//         job.setUpdatedAt(Instant.now());
//         webHookRepository.save(job);

//         saveAttemptLog(job.getId(), attemptNumber, AttemptStatus.SUCCESS, statusCode, null, durationMs, attemptedAt);
//         log.info("Delivered jobId={} on attempt={} with status={}", job.getId(), attemptNumber, statusCode);
//     }

//     private void handleHttpFailure(
//             WebHook job,
//             int attemptNumber,
//             HttpStatusCode httpStatusCode,
//             int statusCode,
//             String message,
//             long durationMs,
//             Instant attemptedAt) {
//         boolean isRetryable = httpStatusCode.is5xxServerError() || statusCode == 429;
//         if (isRetryable) {
//             handleRetryOrFail(job, attemptNumber, statusCode, "HTTP " + statusCode + ": " + message, durationMs,
//                     attemptedAt);
//         } else {
//             job.setStatus(Status.FAILED);
//             job.setUpdatedAt(Instant.now());
//             webHookRepository.save(job);

//             saveAttemptLog(job.getId(), attemptNumber, AttemptStatus.FAILED, statusCode,
//                     "Non-retryable HTTP " + statusCode, durationMs, attemptedAt);
//             log.warn("Failed jobId={} non-retryable status={}", job.getId(), statusCode);
//         }
//     }

//     private void handleRetryOrFail(
//             WebHook job,
//             int attemptNumber,
//             Integer statusCode,
//             String errorMessage,
//             long durationMs,
//             Instant attemptedAt) {
//         int retryIndex = attemptNumber - 1;
//         if (retryIndex < maxRetries) {
//             long delaySeconds = BACKOFF_SECONDS[Math.min(retryIndex, BACKOFF_SECONDS.length - 1)];
//             job.setStatus(Status.RETRYING);
//             job.setNextRunAt(Instant.now().plusSeconds(delaySeconds));
//             job.setUpdatedAt(Instant.now());
//             webHookRepository.save(job);

//             saveAttemptLog(job.getId(), attemptNumber, AttemptStatus.RETRYING, statusCode, errorMessage, durationMs,
//                     attemptedAt);
//             log.info("Retrying jobId={} attempt={} in {}s", job.getId(), attemptNumber, delaySeconds);
//         } else {
//             job.setStatus(Status.FAILED);
//             job.setUpdatedAt(Instant.now());
//             webHookRepository.save(job);

//             saveAttemptLog(job.getId(), attemptNumber, AttemptStatus.FAILED, statusCode, errorMessage, durationMs,
//                     attemptedAt);
//             log.warn("Exhausted retries for jobId={} after attempt={}", job.getId(), attemptNumber);
//         }
//     }

//     private void saveAttemptLog(
//             String jobId,
//             int attemptNumber,
//             AttemptStatus status,
//             Integer httpStatusCode,
//             String errorMessage,
//             long durationMs,
//             Instant attemptedAt) {
//         DeliveryAttempt attempt = new DeliveryAttempt();
//         attempt.setJobId(jobId);
//         attempt.setAttemptNumber(attemptNumber);
//         attempt.setStatus(status);
//         attempt.setHttpStatusCode(httpStatusCode);
//         attempt.setErrorMessage(errorMessage);
//         attempt.setDurationMs(durationMs);
//         attempt.setAttemptedAt(attemptedAt);
//         deliveryAttemptRepository.save(attempt);
//     }
// }




package com.webhook.Webhook.config;

import com.webhook.Webhook.entity.AttemptStatus;
import com.webhook.Webhook.entity.DeliveryAttempt;
import com.webhook.Webhook.entity.Status;
import com.webhook.Webhook.entity.WebHook;
import com.webhook.Webhook.repository.DeliveryAttemptRepo;
import com.webhook.Webhook.repository.WebHookRepo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;
import java.util.List;

@Component
public class WebHookDispatcherWorker {

    private static final Logger log = LoggerFactory.getLogger(WebHookDispatcherWorker.class);
    private static final long[] BACKOFF_SECONDS = { 2L, 4L, 8L };

    private final WebHookRepo webHookRepository;
    private final DeliveryAttemptRepo deliveryAttemptRepository;
    private final DomainRateLimiter domainRateLimiter;
    private final RestClient webhookRestClient;
    private final int maxRetries;

    public WebHookDispatcherWorker(
            WebHookRepo webHookRepository,
            DeliveryAttemptRepo deliveryAttemptRepository,
            DomainRateLimiter domainRateLimiter,
            RestClient webhookRestClient,
            @Value("${webhook.worker.max-retries:3}") int maxRetries) {
        this.webHookRepository = webHookRepository;
        this.deliveryAttemptRepository = deliveryAttemptRepository;
        this.domainRateLimiter = domainRateLimiter;
        this.webhookRestClient = webhookRestClient;
        this.maxRetries = maxRetries;
    }

    @Scheduled(fixedDelayString = "${webhook.worker.poll-interval-ms:500}")
    public void pollAndDispatch() {
        Instant now = Instant.now();
        List<WebHook> dueJobs = webHookRepository.findTop20ByStatusInAndNextRunAtLessThanEqualOrderByNextRunAtAsc(
                List.of(Status.QUEUED, Status.RETRYING),
                now);

        for (WebHook job : dueJobs) {
            processSingleJob(job);
        }
    }

    private void processSingleJob(WebHook job) {
        if (!domainRateLimiter.allowRequest(job.getDestinationDomain())) {
            job.setNextRunAt(Instant.now().plusSeconds(2));
            job.setUpdatedAt(Instant.now());
            webHookRepository.save(job);
            log.info("Rate limited domain={}, rescheduled jobId={}", job.getDestinationDomain(), job.getId());
            return;
        }

        job.setStatus(Status.PROCESSING);
        job.setUpdatedAt(Instant.now());
        webHookRepository.save(job);

        int currentAttempt = job.getAttemptCount() + 1;
        job.setAttemptCount(currentAttempt);

        long startTime = System.currentTimeMillis();
        Instant attemptedAt = Instant.now();

        try {
            ResponseEntity<String> response = webhookRestClient.post()
                    .uri(job.getDestinationUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(job.getPayload())
                    .retrieve()
                    .toEntity(String.class);

            long durationMs = System.currentTimeMillis() - startTime;
            int statusCode = response.getStatusCode().value();

            handleSuccess(job, currentAttempt, statusCode, durationMs, attemptedAt);

        } catch (RestClientResponseException ex) {
            long durationMs = System.currentTimeMillis() - startTime;
            int statusCode = ex.getStatusCode().value();
            handleHttpFailure(job, currentAttempt, ex.getStatusCode(), statusCode, ex.getMessage(), durationMs,
                    attemptedAt);

        } catch (Exception ex) {
            long durationMs = System.currentTimeMillis() - startTime;
            handleRetryOrFail(job, currentAttempt, null, "Timeout or network error: " + ex.getMessage(), durationMs,
                    attemptedAt);
        }
    }

    private void handleSuccess(WebHook job, int attemptNumber, int statusCode, long durationMs, Instant attemptedAt) {
        job.setStatus(Status.COMPLETED);
        job.setUpdatedAt(Instant.now());
        webHookRepository.save(job);

        saveAttemptLog(job.getId(), attemptNumber, AttemptStatus.SUCCESS, statusCode, null, durationMs, attemptedAt);
        log.info("Delivered jobId={} on attempt={} with status={}", job.getId(), attemptNumber, statusCode);
    }

    private void handleHttpFailure(
            WebHook job,
            int attemptNumber,
            HttpStatusCode httpStatusCode,
            int statusCode,
            String message,
            long durationMs,
            Instant attemptedAt) {
        boolean isRetryable = httpStatusCode.is5xxServerError() || statusCode == 429;
        if (isRetryable) {
            handleRetryOrFail(job, attemptNumber, statusCode, "HTTP " + statusCode + ": " + message, durationMs,
                    attemptedAt);
        } else {
            job.setStatus(Status.FAILED);
            job.setUpdatedAt(Instant.now());
            webHookRepository.save(job);

            saveAttemptLog(job.getId(), attemptNumber, AttemptStatus.FAILED, statusCode,
                    "Non-retryable HTTP " + statusCode, durationMs, attemptedAt);
            log.warn("Failed jobId={} non-retryable status={}", job.getId(), statusCode);
        }
    }

    private void handleRetryOrFail(
            WebHook job,
            int attemptNumber,
            Integer statusCode,
            String errorMessage,
            long durationMs,
            Instant attemptedAt) {
        int retryIndex = attemptNumber - 1;
        if (retryIndex < maxRetries) {
            long delaySeconds = BACKOFF_SECONDS[Math.min(retryIndex, BACKOFF_SECONDS.length - 1)];
            job.setStatus(Status.RETRYING);
            job.setNextRunAt(Instant.now().plusSeconds(delaySeconds));
            job.setUpdatedAt(Instant.now());
            webHookRepository.save(job);

            saveAttemptLog(job.getId(), attemptNumber, AttemptStatus.RETRYING, statusCode, errorMessage, durationMs,
                    attemptedAt);
            log.info("Retrying jobId={} attempt={} in {}s", job.getId(), attemptNumber, delaySeconds);
        } else {
            job.setStatus(Status.FAILED);
            job.setUpdatedAt(Instant.now());
            webHookRepository.save(job);

            saveAttemptLog(job.getId(), attemptNumber, AttemptStatus.FAILED, statusCode, errorMessage, durationMs,
                    attemptedAt);
            log.warn("Exhausted retries for jobId={} after attempt={}", job.getId(), attemptNumber);
        }
    }

    private void saveAttemptLog(
            String jobId,
            int attemptNumber,
            AttemptStatus status,
            Integer httpStatusCode,
            String errorMessage,
            long durationMs,
            Instant attemptedAt) {
        DeliveryAttempt attempt = new DeliveryAttempt();
        attempt.setJobId(jobId);
        attempt.setAttemptNumber(attemptNumber);
        attempt.setStatus(status);
        attempt.setHttpStatusCode(httpStatusCode);
        attempt.setErrorMessage(errorMessage);
        attempt.setDurationMs(durationMs);
        attempt.setAttemptedAt(attemptedAt);
        deliveryAttemptRepository.save(attempt);
    }
}