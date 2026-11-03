package com.catsflow.catsflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.catsflow.catsflow.Controller.LineWebhookController;
import com.catsflow.catsflow.Entity.LineUserLink;
import com.catsflow.catsflow.Service.BudgetService;
import com.catsflow.catsflow.Service.LineUserLinkService;
import com.catsflow.catsflow.Service.TransactionSummaryService;
import com.catsflow.catsflow.Service.WebhookEventService;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Optional;

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

    @Test
    void appCommandReturnsMiniAppLiffUrlForLinkedUser() {
        LineUserLinkService lineUserLinkService = mock(LineUserLinkService.class);
        LineUserLink link = new LineUserLink();
        link.setUserId(12L);
        when(lineUserLinkService.findByLineUserId("U_verified")).thenReturn(Optional.of(link));
        LineWebhookController controller = new LineWebhookController(
                lineUserLinkService,
                mock(TransactionSummaryService.class),
                mock(BudgetService.class),
                mock(WebhookEventService.class),
                new ObjectMapper());
        ReflectionTestUtils.setField(controller, "liffId", "2012355860-GSuAbOEZ");

        String reply = ReflectionTestUtils.invokeMethod(controller, "buildReplyText", "U_verified", "アプリ");

        assertThat(reply).isEqualTo("Catsflow Mini Appを開く: https://liff.line.me/2012355860-GSuAbOEZ");
    }

    @Test
    void openCommandReturnsSetupMessageWhenLiffIdIsMissing() {
        LineUserLinkService lineUserLinkService = mock(LineUserLinkService.class);
        LineUserLink link = new LineUserLink();
        link.setUserId(12L);
        when(lineUserLinkService.findByLineUserId("U_verified")).thenReturn(Optional.of(link));
        LineWebhookController controller = new LineWebhookController(
                lineUserLinkService,
                mock(TransactionSummaryService.class),
                mock(BudgetService.class),
                mock(WebhookEventService.class),
                new ObjectMapper());

        String reply = ReflectionTestUtils.invokeMethod(controller, "buildReplyText", "U_verified", "開く");

        assertThat(reply).isEqualTo("Mini Appのリンクが設定されていません。管理者に連絡してください。");
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
