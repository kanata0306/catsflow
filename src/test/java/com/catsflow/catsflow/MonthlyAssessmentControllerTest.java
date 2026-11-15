package com.catsflow.catsflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.catsflow.catsflow.Controller.MonthlyAssessmentController;
import com.catsflow.catsflow.Entity.LineUserLink;
import com.catsflow.catsflow.Service.LineIdTokenVerifier;
import com.catsflow.catsflow.Service.LineUserLinkService;
import com.catsflow.catsflow.Service.MonthlyAssessmentService;
import java.time.YearMonth;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class MonthlyAssessmentControllerTest {

    @Mock
    private MonthlyAssessmentService monthlyAssessmentService;

    @Mock
    private LineIdTokenVerifier lineIdTokenVerifier;

    @Mock
    private LineUserLinkService lineUserLinkService;

    @InjectMocks
    private MonthlyAssessmentController controller;

    @Test
    void requiresLinkedLineIdentity() {
        var response = controller.getMonthlyAssessment(null, "2026-10");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rejectsMalformedMonth() {
        authorizeUser();

        var response = controller.getMonthlyAssessment("Bearer valid-token", "October 2026");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void scopesMonthlyAssessmentToLinkedUser() {
        authorizeUser();

        var response = controller.getMonthlyAssessment("Bearer valid-token", "2026-10");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(monthlyAssessmentService).evaluate(7L, YearMonth.of(2026, 10));
    }

    private void authorizeUser() {
        LineUserLink link = new LineUserLink();
        link.setUserId(7L);
        link.setLineUserId("U123");
        when(lineIdTokenVerifier.verify("valid-token")).thenReturn(Optional.of("U123"));
        when(lineUserLinkService.findByLineUserId("U123")).thenReturn(Optional.of(link));
    }
}