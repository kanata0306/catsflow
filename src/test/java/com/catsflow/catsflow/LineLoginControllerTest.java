package com.catsflow.catsflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.catsflow.catsflow.Controller.LineLoginController;
import com.catsflow.catsflow.Controller.LineLoginController.LoginRequest;
import com.catsflow.catsflow.Service.LineAccountService;
import com.catsflow.catsflow.Service.LineAccountService.LoginResult;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class LineLoginControllerTest {

    @Mock
    private LineAccountService lineAccountService;

    @InjectMocks
    private LineLoginController controller;

    @Test
    void loginPageReturnsLineLoginView() {
        assertThat(controller.loginPage()).isEqualTo("line-login");
    }

    @Test
    void configReturnsConfiguredLiffId() {
        ReflectionTestUtils.setField(controller, "liffId", "1234567890-abcdefgh");

        Map<String, String> config = controller.config();

        assertThat(config).containsEntry("liffId", "1234567890-abcdefgh");
    }

    @Test
    void configReturnsEmptyStringWhenLiffIdIsNull() {
        ReflectionTestUtils.setField(controller, "liffId", null);

        Map<String, String> config = controller.config();

        assertThat(config).containsEntry("liffId", "");
    }

    @Test
    void authenticateReturnsBadRequestWhenRequestIsNull() {
        ResponseEntity<Map<String, Object>> response = controller.authenticate(null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsKey("message");
    }

    @Test
    void authenticateReturnsUnauthorizedWhenLoginFails() {
        when(lineAccountService.loginOrRegister(anyString(), anyString(), anyString(), any()))
                .thenReturn(new LoginResult(false, "認証に失敗しました。"));
        LoginRequest request = new LoginRequest("token", "user@example.com", "password12345", "name");

        ResponseEntity<Map<String, Object>> response = controller.authenticate(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("message", "認証に失敗しました。");
    }

    @Test
    void authenticateReturnsOkWhenLoginSucceeds() {
        when(lineAccountService.loginOrRegister(anyString(), anyString(), anyString(), any()))
                .thenReturn(new LoginResult(true, "連携しました。"));
        LoginRequest request = new LoginRequest("token", "user@example.com", "password12345", "name");

        ResponseEntity<Map<String, Object>> response = controller.authenticate(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("message", "連携しました。");
    }
}
