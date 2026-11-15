package com.catsflow.catsflow.Controller;

import com.catsflow.catsflow.Entity.LineUserLink;
import com.catsflow.catsflow.Service.LineIdTokenVerifier;
import com.catsflow.catsflow.Service.LineUserLinkService;
import com.catsflow.catsflow.Service.MonthlyAssessmentService;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assessment")
public class MonthlyAssessmentController {

    private final MonthlyAssessmentService monthlyAssessmentService;
    private final LineIdTokenVerifier lineIdTokenVerifier;
    private final LineUserLinkService lineUserLinkService;

    public MonthlyAssessmentController(MonthlyAssessmentService monthlyAssessmentService,
            LineIdTokenVerifier lineIdTokenVerifier,
            LineUserLinkService lineUserLinkService) {
        this.monthlyAssessmentService = monthlyAssessmentService;
        this.lineIdTokenVerifier = lineIdTokenVerifier;
        this.lineUserLinkService = lineUserLinkService;
    }

    @GetMapping("/monthly")
    public ResponseEntity<?> getMonthlyAssessment(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String month) {
        Optional<Long> userId = authenticate(authorization);
        if (userId.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "LINE認証とCatsflowアカウント連携を確認してください。"));
        }

        YearMonth targetMonth;
        try {
            targetMonth = month == null || month.isBlank() ? YearMonth.now() : YearMonth.parse(month);
        } catch (DateTimeParseException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", "対象年月はYYYY-MM形式で指定してください。"));
        }
        return ResponseEntity.ok(monthlyAssessmentService.evaluate(userId.get(), targetMonth));
    }

    private Optional<Long> authenticate(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return Optional.empty();
        }
        Optional<String> lineUserId = lineIdTokenVerifier.verify(authorization.substring(7));
        if (lineUserId.isEmpty()) {
            return Optional.empty();
        }
        return lineUserLinkService.findByLineUserId(lineUserId.get()).map(LineUserLink::getUserId);
    }
}