package com.catsflow.catsflow.Controller;

import com.catsflow.catsflow.Entity.LineUserLink;
import com.catsflow.catsflow.Service.BudgetService;
import com.catsflow.catsflow.Service.LineUserLinkService;
import com.catsflow.catsflow.Service.TransactionSummaryService;
import com.catsflow.catsflow.Service.WebhookEventService;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("/api/line")
public class LineWebhookController {

    private static final String SIGNATURE_HEADER = "x-line-signature";

    private final LineUserLinkService lineUserLinkService;
    private final TransactionSummaryService transactionSummaryService;
    private final BudgetService budgetService;
    private final WebhookEventService webhookEventService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${line.channel.secret:dev-secret}")
    private String channelSecret;

    @Value("${line.channel.token:dummy-token}")
    private String channelToken;

    @Value("${line.liff.id:}")
    private String liffId;

    public LineWebhookController(LineUserLinkService lineUserLinkService,
            TransactionSummaryService transactionSummaryService,
            BudgetService budgetService,
            WebhookEventService webhookEventService,
            ObjectMapper objectMapper) {
        this.lineUserLinkService = lineUserLinkService;
        this.transactionSummaryService = transactionSummaryService;
        this.budgetService = budgetService;
        this.webhookEventService = webhookEventService;
        this.objectMapper = objectMapper;
    }

    /**
     * LINEのWebhookを受信し、署名検証後にイベントごとに応答メッセージを返す。
     * 初期段階では「今月」「予算」「カテゴリ別」コマンドに対する最小応答を扱う。
     */
    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> webhook(
            @RequestHeader(value = SIGNATURE_HEADER, required = false) String signature,
            @RequestBody String rawBody) {

        if (signature == null || signature.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("ok", false, "message", "missing line signature"));
        }

        if (!isValidSignature(signature, rawBody)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("ok", false, "message", "invalid line signature"));
        }

        Map<String, Object> payload;
        try {
            payload = objectMapper.readValue(rawBody, new TypeReference<>() {
            });
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("ok", false, "message", "invalid webhook payload"));
        }

        List<Map<String, Object>> events = extractEvents(payload);
        for (Map<String, Object> event : events) {
            String eventType = String.valueOf(event.getOrDefault("type", ""));
            String eventId = extractEventId(event);
            if (!"message".equals(eventType)) {
                continue;
            }
            if (!eventId.isBlank() && !webhookEventService.shouldProcess(eventId)) {
                continue;
            }

            String userId = extractUserId(event);
            String replyToken = String.valueOf(event.getOrDefault("replyToken", ""));
            String text = extractText(event);
            String replyText = buildReplyText(userId, text);

            if (!replyToken.isBlank() && !replyText.isBlank() && !"dummy-token".equals(channelToken)) {
                sendReply(replyToken, replyText);
            }

            if (!eventId.isBlank()) {
                webhookEventService.markProcessed(eventId, userId, eventType);
            }
        }

        return ResponseEntity.ok(Map.of("ok", true, "message", "webhook accepted"));
    }

    private String buildReplyText(String lineUserId, String text) {
        Optional<LineUserLink> linkedUser = lineUserLinkService.findByLineUserId(lineUserId);
        if (linkedUser.isEmpty()) {
            if (liffId == null || liffId.isBlank()) {
                return "CatsflowのLINEログイン設定がまだ完了していません。管理者に連絡してください。";
            }
            return "初回ログインはこちら: https://liff.line.me/" + liffId;
        }

        Long userId = linkedUser.get().getUserId();
        String normalized = text == null ? "" : text.trim();

        if ("今月".equals(normalized)) {
            var summary = transactionSummaryService.getMonthlySummary(userId);
            return "今月の収入: " + formatMoney(summary.getIncome())
                    + "\n支出: " + formatMoney(summary.getExpense())
                    + "\n収支: " + formatMoney(summary.getBalance());
        }

        if ("カテゴリ別".equals(normalized)) {
            var categories = transactionSummaryService.getCategorySummary(userId);
            if (categories.isEmpty()) {
                return "今月の支出データがまだありません。";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("カテゴリ別支出\n");
            for (int i = 0; i < Math.min(categories.size(), 5); i++) {
                var category = categories.get(i);
                sb.append(i + 1)
                        .append(". ")
                        .append(category.getCategoryName())
                        .append(": ")
                        .append(formatMoney(category.getTotal()))
                        .append("\n");
            }
            return sb.toString().trim();
        }

        if ("予算".equals(normalized)) {
            var budgetStatus = budgetService.getCurrentMonthBudgetStatus(userId);
            if (budgetStatus == null) {
                return "今月の予算はまだ設定されていません。Mini Appで予算を登録してください。";
            }
            return "今月の予算: " + formatMoney(budgetStatus.budgetAmount())
                    + "\n使用額: " + formatMoney(budgetStatus.spentAmount())
                    + "\n残額: " + formatMoney(budgetStatus.remaining())
                    + "\n使用率: " + budgetStatus.usageRate() + "%";
        }

        return "利用可能なコマンドは\n今月\n予算\nカテゴリ別\nです。";
    }

    private void sendReply(String replyToken, String text) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(channelToken);

        Map<String, Object> payload = Map.of(
                "replyToken", replyToken,
                "messages", List.of(Map.of("type", "text", "text", text)));

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
        restTemplate.postForEntity("https://api.line.me/v2/bot/message/reply", request, String.class);
    }

    private boolean isValidSignature(String signature, String rawBody) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec key = new SecretKeySpec(channelSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(key);
            byte[] expected = Base64.getEncoder().encode(mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(expected, signature.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractEvents(Map<String, Object> payload) {
        Object rawEvents = payload.get("events");
        if (rawEvents instanceof List<?> list) {
            List<Map<String, Object>> events = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    events.add((Map<String, Object>) map);
                }
            }
            return events;
        }
        return List.of();
    }

    private String extractEventId(Map<String, Object> event) {
        Object eventId = event.get("webhookEventId");
        if (eventId == null) {
            eventId = event.get("eventId");
        }
        if (eventId != null) {
            return String.valueOf(eventId);
        }
        return "";
    }

    private String extractUserId(Map<String, Object> event) {
        Object source = event.get("source");
        if (source instanceof Map<?, ?> sourceMap) {
            Object userId = sourceMap.get("userId");
            if (userId != null) {
                return String.valueOf(userId);
            }
        }
        return "";
    }

    private String extractText(Map<String, Object> event) {
        Object message = event.get("message");
        if (message instanceof Map<?, ?> messageMap) {
            Object text = messageMap.get("text");
            if (text != null) {
                return String.valueOf(text);
            }
        }
        return "";
    }

    private String formatMoney(BigDecimal value) {
        if (value == null) {
            return "¥0";
        }
        return "¥" + String.format("%,.0f", value);
    }
}
