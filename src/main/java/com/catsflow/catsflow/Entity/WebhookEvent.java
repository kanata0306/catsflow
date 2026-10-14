package com.catsflow.catsflow.Entity;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class WebhookEvent {
    private Long id;
    private String eventId;
    private String userId;
    private String eventType;
    private String payloadHash;
    private LocalDateTime processedAt;
}
