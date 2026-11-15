package com.catsflow.catsflow.Controller;

import com.catsflow.catsflow.Entity.LineUserLink;
import com.catsflow.catsflow.Service.LineIdTokenVerifier;
import com.catsflow.catsflow.Service.LineUserLinkService;
import com.catsflow.catsflow.Service.PlanningDataService;
import com.catsflow.catsflow.Service.PlanningDataService.FinancialGoal;
import com.catsflow.catsflow.Service.PlanningDataService.FinancialProfile;
import com.catsflow.catsflow.Service.PlanningDataService.GoalInput;
import com.catsflow.catsflow.Service.PlanningDataService.LifeEventInput;
import com.catsflow.catsflow.Service.PlanningDataService.LifePlanEvent;
import com.catsflow.catsflow.Service.PlanningDataService.LiabilityInput;
import com.catsflow.catsflow.Service.PlanningDataService.LongTermLiability;
import com.catsflow.catsflow.Service.PlanningDataService.ProfileInput;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/planning")
public class PlanningController {

    private final PlanningDataService planningDataService;
    private final LineIdTokenVerifier lineIdTokenVerifier;
    private final LineUserLinkService lineUserLinkService;

    public PlanningController(PlanningDataService planningDataService,
            LineIdTokenVerifier lineIdTokenVerifier,
            LineUserLinkService lineUserLinkService) {
        this.planningDataService = planningDataService;
        this.lineIdTokenVerifier = lineIdTokenVerifier;
        this.lineUserLinkService = lineUserLinkService;
    }

    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Optional<Long> userId = authenticate(authorization);
        if (userId.isEmpty())
            return unauthorized();
        return ResponseEntity.ok(new ItemResponse<>(planningDataService.findProfile(userId.get()).orElse(null)));
    }

    @PutMapping("/profile")
    public ResponseEntity<?> saveProfile(@RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody ProfileInput input) {
        Optional<Long> userId = authenticate(authorization);
        if (userId.isEmpty())
            return unauthorized();
        try {
            return ResponseEntity.ok(new ItemResponse<>(planningDataService.saveProfile(userId.get(), input)));
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        }
    }

    @GetMapping("/liabilities")
    public ResponseEntity<?> getLiabilities(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Optional<Long> userId = authenticate(authorization);
        if (userId.isEmpty())
            return unauthorized();
        return ResponseEntity.ok(new ListResponse<>(planningDataService.findLiabilities(userId.get())));
    }

    @PostMapping("/liabilities")
    public ResponseEntity<?> createLiability(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody LiabilityInput input) {
        Optional<Long> userId = authenticate(authorization);
        if (userId.isEmpty())
            return unauthorized();
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new ItemResponse<>(planningDataService.createLiability(userId.get(), input)));
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        }
    }

    @PutMapping("/liabilities/{id}")
    public ResponseEntity<?> updateLiability(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id, @RequestBody LiabilityInput input) {
        Optional<Long> userId = authenticate(authorization);
        if (userId.isEmpty())
            return unauthorized();
        try {
            return planningDataService.updateLiability(userId.get(), id, input)
                    .<ResponseEntity<?>>map(value -> ResponseEntity.ok(new ItemResponse<>(value)))
                    .orElseGet(() -> notFound("負債が見つかりません。"));
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        }
    }

    @DeleteMapping("/liabilities/{id}")
    public ResponseEntity<?> deleteLiability(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id) {
        Optional<Long> userId = authenticate(authorization);
        if (userId.isEmpty())
            return unauthorized();
        return planningDataService.deleteLiability(userId.get(), id)
                ? ResponseEntity.noContent().build()
                : notFound("負債が見つかりません。");
    }

    @GetMapping("/goals")
    public ResponseEntity<?> getGoals(@RequestHeader(value = "Authorization", required = false) String authorization) {
        Optional<Long> userId = authenticate(authorization);
        if (userId.isEmpty())
            return unauthorized();
        return ResponseEntity.ok(new ListResponse<>(planningDataService.findGoals(userId.get())));
    }

    @PostMapping("/goals")
    public ResponseEntity<?> createGoal(@RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody GoalInput input) {
        Optional<Long> userId = authenticate(authorization);
        if (userId.isEmpty())
            return unauthorized();
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new ItemResponse<>(planningDataService.createGoal(userId.get(), input)));
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        }
    }

    @PutMapping("/goals/{id}")
    public ResponseEntity<?> updateGoal(@RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id, @RequestBody GoalInput input) {
        Optional<Long> userId = authenticate(authorization);
        if (userId.isEmpty())
            return unauthorized();
        try {
            return planningDataService.updateGoal(userId.get(), id, input)
                    .<ResponseEntity<?>>map(value -> ResponseEntity.ok(new ItemResponse<>(value)))
                    .orElseGet(() -> notFound("目標が見つかりません。"));
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        }
    }

    @DeleteMapping("/goals/{id}")
    public ResponseEntity<?> deleteGoal(@RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id) {
        Optional<Long> userId = authenticate(authorization);
        if (userId.isEmpty())
            return unauthorized();
        return planningDataService.deleteGoal(userId.get(), id)
                ? ResponseEntity.noContent().build()
                : notFound("目標が見つかりません。");
    }

    @GetMapping("/life-events")
    public ResponseEntity<?> getLifeEvents(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Optional<Long> userId = authenticate(authorization);
        if (userId.isEmpty())
            return unauthorized();
        return ResponseEntity.ok(new ListResponse<>(planningDataService.findLifePlanEvents(userId.get())));
    }

    @PostMapping("/life-events")
    public ResponseEntity<?> createLifeEvent(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody LifeEventInput input) {
        Optional<Long> userId = authenticate(authorization);
        if (userId.isEmpty())
            return unauthorized();
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new ItemResponse<>(planningDataService.createLifePlanEvent(userId.get(), input)));
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        }
    }

    @PutMapping("/life-events/{id}")
    public ResponseEntity<?> updateLifeEvent(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id, @RequestBody LifeEventInput input) {
        Optional<Long> userId = authenticate(authorization);
        if (userId.isEmpty())
            return unauthorized();
        try {
            return planningDataService.updateLifePlanEvent(userId.get(), id, input)
                    .<ResponseEntity<?>>map(value -> ResponseEntity.ok(new ItemResponse<>(value)))
                    .orElseGet(() -> notFound("ライフイベントが見つかりません。"));
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        }
    }

    @DeleteMapping("/life-events/{id}")
    public ResponseEntity<?> deleteLifeEvent(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id) {
        Optional<Long> userId = authenticate(authorization);
        if (userId.isEmpty())
            return unauthorized();
        return planningDataService.deleteLifePlanEvent(userId.get(), id)
                ? ResponseEntity.noContent().build()
                : notFound("ライフイベントが見つかりません。");
    }

    private Optional<Long> authenticate(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer "))
            return Optional.empty();
        Optional<String> lineUserId = lineIdTokenVerifier.verify(authorization.substring(7));
        if (lineUserId.isEmpty())
            return Optional.empty();
        Optional<LineUserLink> link = lineUserLinkService.findByLineUserId(lineUserId.get());
        return link.map(LineUserLink::getUserId);
    }

    private ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException error) {
        return ResponseEntity.badRequest().body(Map.of("message", error.getMessage()));
    }

    private ResponseEntity<Map<String, String>> notFound(String message) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", message));
    }

    private ResponseEntity<Map<String, String>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", "LINE認証とCatsflowアカウント連携を確認してください。"));
    }

    public record ItemResponse<T>(T item) {
    }

    public record ListResponse<T>(List<T> items) {
    }
}