// package com.webhook.Webhook.service;

// import java.net.URI;
// import java.time.Instant;
// import java.util.UUID;

// import org.springframework.stereotype.Service;

// import com.webhook.Webhook.dto.DispatchRequest;
// import com.webhook.Webhook.dto.DispatchResponse;
// import com.webhook.Webhook.entity.Status;
// import com.webhook.Webhook.entity.WebHook;
// import com.webhook.Webhook.repository.WebHookRepo;

// @Service
// public class WebhookService {

//     private final WebHookRepo webHookRepo;

//     public WebhookService(WebHookRepo webHookRepo) {
//         this.webHookRepo = webHookRepo;
//     }

//     public DispatchResponse enqueueWebhook(DispatchRequest request) {
//         String jobId = UUID.randomUUID().toString();
//         Instant now = Instant.now();
//         String domain = extractDomain(request.getDestinationUrl());

//         WebHook webHook = new WebHook();
//         webHook.setId(jobId);
//         webHook.setEventId(request.getEventId());
//         webHook.setDestinationUrl(request.getDestinationUrl());
//         webHook.setDestinationDomain(domain);
//         webHook.setPayload(request.getPayload());
//         webHook.setStatus(Status.QUEUED);
//         webHook.setAttemptCount(0);
//         webHook.setNextRunAt(now);
//         webHook.setCreatedAt(now);
//         webHook.setUpdatedAt(now);

//         webHookRepo.save(webHook);

//         return new DispatchResponse("queued", jobId);
//     }

//     private String extractDomain(String url) {
//         try {
//             URI uri = URI.create(url);
//             String host = uri.getHost();
//             if (host == null || host.isBlank()) {
//                 throw new IllegalArgumentException("Invalid destinationUrl host");
//             }
//             return host.toLowerCase();
//         } catch (Exception ex) {
//             throw new IllegalArgumentException("Invalid destinationUrl: " + url);
//         }
//     }
// }


package com.webhook.Webhook.service;

import java.net.URI;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.webhook.Webhook.dto.DispatchRequest;
import com.webhook.Webhook.dto.DispatchResponse;
import com.webhook.Webhook.entity.Status;
import com.webhook.Webhook.entity.WebHook;
import com.webhook.Webhook.repository.WebHookRepo;

@Service
public class WebhookService {

    private final WebHookRepo webHookRepo;

    public WebhookService(WebHookRepo webHookRepo) {
        this.webHookRepo = webHookRepo;
    }

    public DispatchResponse enqueueWebhook(DispatchRequest request) {
        String jobId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        String domain = extractAndValidateDomain(request.getDestinationUrl());

        WebHook webHook = new WebHook();
        webHook.setId(jobId);
        webHook.setEventId(request.getEventId());
        webHook.setDestinationUrl(request.getDestinationUrl().trim());
        webHook.setDestinationDomain(domain);
        webHook.setPayload(request.getPayload());
        webHook.setStatus(Status.QUEUED);
        webHook.setAttemptCount(0);
        webHook.setNextRunAt(now);
        webHook.setCreatedAt(now);
        webHook.setUpdatedAt(now);

        webHookRepo.save(webHook);

        return new DispatchResponse("queued", jobId);
    }

    private String extractAndValidateDomain(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("destinationUrl must not be blank");
        }
        try {
            URI uri = URI.create(url.trim());
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                throw new IllegalArgumentException("destinationUrl must use http or https");
            }
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                throw new IllegalArgumentException("Invalid destinationUrl host");
            }
            return host.toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid destinationUrl: " + url);
        }
    }
}