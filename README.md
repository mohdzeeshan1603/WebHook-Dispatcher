# Distributed Webhook Dispatcher Engine

A reliable, non-blocking **Distributed Webhook Dispatcher Engine** built with **Java 25** and **Spring Boot**. Designed for modern distributed systems, this service ingests internal webhook events, queues them asynchronously, dispatches them to third-party endpoints with exponential backoff retries, enforces per-domain rate limits, and logs every delivery attempt.

## Live Deployment & Deliverables

* **Live API Base URL:** `https://webhook-dispatcher-production.up.railway.app`
* **Ingestion Endpoint:** `POST https://webhook-dispatcher-production.up.railway.app/api/webhooks/dispatch`
* **Source Code:** Available in this repository under `src/main/java/com/webhook/Webhook`

## Core Requirements & Behavior

### 1. Ingestion Endpoint (`POST /api/webhooks/dispatch`)

* **Validation & Non-Blocking Response:** Validates incoming JSON (`destinationUrl`, `eventId`, `payload`), queues the job for background processing, and immediately responds with **`202 Accepted`** (`{ "status": "queued", "jobId": "..." }`) without waiting for the downstream HTTP call to finish.
* **Latency Guarantee:** Even if the target `destinationUrl` takes 10+ seconds to respond, the ingestion endpoint returns `202 Accepted` in milliseconds.

### 2. Async Background Worker & Exponential Backoff

* A background worker asynchronously polls queued jobs and sends an HTTP `POST` request with the event payload to `destinationUrl`.
* **Retry Policy:** On downstream `5xx` server errors, `429 Too Many Requests`, or network timeouts, the worker retries delivery up to **3 times** with exponential backoff:
  * Attempt 1 retry: **2 seconds**
  * Attempt 2 retry: **4 seconds**
  * Attempt 3 retry: **8 seconds**
* **Delivery Logging:** Tracks every delivery attempt with its status (`success`, `retrying`, `failed`), HTTP response status code, and attempt count.

### 3. Rate Limiting Protection

* Enforces per-domain rate limiting (token-bucket / sliding-window keyed by destination domain) so the dispatcher never floods any single client domain with rapid-fire concurrent requests.

## Architecture & Design Choices

1. **Non-Blocking Ingestion & Asynchronous Execution**
   * `WebhookAPI` accepts the request, validates fields via Jakarta Bean Validation (`@Valid`), and hands off creation to `WebhookService`.
   * `WebhookService` persists the job with `nextRunAt = now` and immediately returns `202 Accepted`. A background worker independently polls due jobs (`nextRunAt <= now`) and performs outbound HTTP calls off the request thread.

2. **State Persistence (`ConcurrentHashMap` In-Memory Store)**
   * Jobs (`WebHook`) and attempt histories (`DeliveryAttempt`) are stored in thread-safe `ConcurrentHashMap`-backed stores (`InMemoryWebHookStore` and `InMemoryDeliveryAttemptStore`).
   * This eliminates external database connection latency and authentication overhead on free-tier hosting while keeping storage interfaces modular for future persistence backends (e.g., PostgreSQL/Redis).

3. **Error Handling & Retry Scheduling**
   * **2xx Responses:** Job is marked succeeded and the attempt is logged.
   * **429 / 5xx / Timeouts:** Increments `attemptCount`; if attempts remain ($\le 3$), schedules `nextRunAt` using exponential backoff ($2^n$ seconds: `2s`, `4s`, `8s`) and marks status as `RETRYING`. After 3 failed retries, marks status as `FAILED`.
   * **Non-Retryable 4xx (except 429):** Fails immediately without unnecessary retries and records the response status.

4. **Per-Domain Rate Limiting**
   * Before dispatching a job, the worker checks the domain limiter extracted from `destinationUrl`. If the rate limit threshold is reached, the job is deferred briefly (`nextRunAt` pushed forward) rather than blocking worker threads or dropping the event.

## Project Structure

```text
src/main/java/com/webhook/Webhook/
├── api/
│   ├── WebhookAPI.java                   # POST /api/webhooks/dispatch & error handling             
├── dto/
│   ├── DispatchRequest.java              # Incoming webhook request DTO
│   └── DispatchResponse.java             # 202 Accepted response DTO
├── entity/
│   ├── WebHook.java                      # Webhook job state, domain & retry metadata
│   ├── DeliveryAttempt.java              # Individual delivery attempt audit log
│   └── Status.java                       # Job & attempt lifecycle statuses
├── repository/
│   ├── WebHookRepo.java                  # ConcurrentHashMap store for webhook jobs
│   └── DeliveryAttempRepo.java.          # ConcurrentHashMap store for delivery attempts
└── service/
    └── WebhookService.java               # Queueing, background worker, backoff & rate limiter
```

## Testing Instructions (`curl`)

### 1. Standard Webhook Dispatch (Matches PDF Spec)

Replace `https://target-server.com/endpoint` with any valid client receiver URL:

```bash
curl -i -X POST https://webhook-dispatcher-production.up.railway.app/api/webhooks/dispatch \
  -H "Content-Type: application/json" \
  -d '{
    "destinationUrl": "https://target-server.com/endpoint",
    "eventId": "evt_123456",
    "payload": {
      "event": "user.signup",
      "userId": "usr_999"
    }
  }'
```

**Expected Immediate Response (`202 Accepted`):**

```json
{
  "status": "queued",
  "jobId": "<generated-job-id>"
}
```

### 2. Verify Non-Blocking Behavior (Slow Downstream Server)

Test against an endpoint that delays its response by 10 seconds:

```bash
curl -i -X POST https://webhook-dispatcher-production.up.railway.app/api/webhooks/dispatch \
  -H "Content-Type: application/json" \
  -d '{
    "destinationUrl": "https://httpbin.org/delay/10",
    "eventId": "evt_slow_10s",
    "payload": {
      "event": "user.signup",
      "userId": "usr_999"
    }
  }'
```

* The `POST /api/webhooks/dispatch` call still returns `202 Accepted` immediately without waiting 10 seconds.

### 3. Verify Exponential Backoff Retries (`5xx` / `429`)

Test against an endpoint returning `500` or `429`:

```bash
curl -i -X POST https://webhook-dispatcher-production.up.railway.app/api/webhooks/dispatch \
  -H "Content-Type: application/json" \
  -d '{
    "destinationUrl": "https://httpstat.us/500",
    "eventId": "evt_retry_500",
    "payload": {
      "event": "user.signup",
      "userId": "usr_999"
    }
  }'
```

* The background worker attempts delivery and retries up to 3 times with `2s`, `4s`, and `8s` backoff intervals.

### 4. Verify Validation Error (`400 Bad Request`)

Sending an invalid or missing `destinationUrl` / `eventId` returns `400 Bad Request`:

```bash
curl -i -X POST https://webhook-dispatcher-production.up.railway.app/api/webhooks/dispatch \
  -H "Content-Type: application/json" \
  -d '{
    "destinationUrl": "",
    "eventId": ""
  }'
```

## Running Locally

### Prerequisites

* **Java 25**
* **Maven** (or included `./mvnw` wrapper)

### Start the Server

```bash
git clone <your-github-repo-url>
cd Webhook
./mvnw spring-boot:run
```

The local server starts at `http://localhost:8080`.
