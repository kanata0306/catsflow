package com.catsflow.catsflow.Service;

import com.catsflow.catsflow.Entity.WebhookEvent;
import com.catsflow.catsflow.Repository.WebhookEventRepository;
import org.springframework.stereotype.Service;

@Service
public class WebhookEventService {

    private final WebhookEventRepository webhookEventRepository;

    public WebhookEventService(WebhookEventRepository webhookEventRepository) {
        this.webhookEventRepository = webhookEventRepository;
    }

    public boolean shouldProcess(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return true;
        }
        return !webhookEventRepository.existsByEventId(eventId);
    }

    public void markProcessed(String eventId, String userId, String eventType) {
        if (eventId == null || eventId.isBlank()) {
            return;
        }
        if (webhookEventRepository.existsByEventId(eventId)) {
            return;
        }

        WebhookEvent event = new WebhookEvent();
        event.setEventId(eventId);
        event.setUserId(userId);
        event.setEventType(eventType);
        event.setPayloadHash(Integer.toHexString(eventId.hashCode()));
        webhookEventRepository.save(event);
    }
}
