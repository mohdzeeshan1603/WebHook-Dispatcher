package com.webhook.Webhook.api;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.webhook.Webhook.dto.DispatchRequest;
import com.webhook.Webhook.dto.DispatchResponse;
import com.webhook.Webhook.service.WebhookService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/webhooks")
public class WebhookAPI {

    private final WebhookService webhookService;

    public WebhookAPI(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping("/dispatch")
    public ResponseEntity<DispatchResponse> dispatch(@Valid @RequestBody DispatchRequest request) {
        DispatchResponse response = webhookService.enqueueWebhook(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }
}
