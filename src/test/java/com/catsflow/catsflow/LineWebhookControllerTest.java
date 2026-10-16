package com.catsflow.catsflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.catsflow.catsflow.Controller.LineWebhookController;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

class LineWebhookControllerTest {

    private static final String SECRET = "test-channel-secret";

    @Test
    void acceptsSignatureForExactRawJsonBody() throws Exception {
        String rawBody = "{ \"events\" : [] }";

        var response = controller().webhook(sign(rawBody), rawBody);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void rejectsSignatureThatDoesNotMatchRawBody() throws Exception {
        var response = controller().webhook(sign("{\"events\":[]}"), "{ \"events\" : [] }");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private LineWebhookController controller() {
        LineWebhookController controller = new LineWebhookController(null, null, null, null, new ObjectMapper());
        ReflectionTestUtils.setField(controller, "channelSecret", SECRET);
        return controller;
    }

    private String sign(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
}
