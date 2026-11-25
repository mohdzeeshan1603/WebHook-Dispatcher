package com.webhook.Webhook.dto;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DispatchRequest {
    @NotBlank(message = "destinationUrl is required")
    @Pattern(regexp = "^https?://.+", message = "destinationUrl must start with http:// or https://")
    private String destinationUrl;

    @NotBlank(message = "eventId is required")
    private String eventId;

    @NotNull(message = "payload is required")
    private Map<String, Object> payload;
}
