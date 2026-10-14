package com.catsflow.catsflow.Controller;

import com.catsflow.catsflow.Service.LineAccountService;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class LineLoginController {

    private final LineAccountService lineAccountService;

    @Value("${line.liff.id:}")
    private String liffId;

    public LineLoginController(LineAccountService lineAccountService) {
        this.lineAccountService = lineAccountService;
    }

    @GetMapping("/line/login")
    public String loginPage() {
        return "line-login";
    }

    @GetMapping("/api/line/config")
    @ResponseBody
    public Map<String, String> config() {
        return Map.of("liffId", liffId == null ? "" : liffId);
    }

    @PostMapping("/api/line/authenticate")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> authenticate(@RequestBody LoginRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "入力内容を確認してください。"));
        }

        LineAccountService.LoginResult result = lineAccountService.loginOrRegister(
                request.idToken(), request.email(), request.password(), request.name());
        if (!result.successful()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", result.message()));
        }
        return ResponseEntity.ok(Map.of("message", result.message()));
    }

    public record LoginRequest(String idToken, String email, String password, String name) {
    }
}
