package com.catsflow.catsflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.catsflow.catsflow.Service.WebhookEventService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class WebhookEventServiceTest {

    @Autowired
    private WebhookEventService webhookEventService;

    @Test
    void duplicateEventIdIsRejected() {
        String eventId = "evt-duplicate-check-001";

        assertThat(webhookEventService.shouldProcess(eventId)).isTrue();
        webhookEventService.markProcessed(eventId, "U-1", "message");
        assertThat(webhookEventService.shouldProcess(eventId)).isFalse();
    }
}
