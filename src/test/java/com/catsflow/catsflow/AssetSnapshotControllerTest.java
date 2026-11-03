package com.catsflow.catsflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.catsflow.catsflow.Controller.AssetSnapshotController;
import com.catsflow.catsflow.Entity.LineUserLink;
import com.catsflow.catsflow.Service.AssetSnapshotService;
import com.catsflow.catsflow.Service.AssetSnapshotService.AssetSnapshot;
import com.catsflow.catsflow.Service.LineIdTokenVerifier;
import com.catsflow.catsflow.Service.LineUserLinkService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class AssetSnapshotControllerTest {

    @Mock
    private AssetSnapshotService assetSnapshotService;

    @Mock
    private LineIdTokenVerifier lineIdTokenVerifier;

    @Mock
    private LineUserLinkService lineUserLinkService;

    @InjectMocks
    private AssetSnapshotController controller;

    @Test
    void rejectsRequestsWithoutAuthorization() {
        ResponseEntity<Map<String, Object>> response = controller.getSnapshots(null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void returnsSnapshotsForTheLinkedUser() {
        LineUserLink link = new LineUserLink();
        link.setUserId(7L);
        link.setLineUserId("U123");
        AssetSnapshot snapshot = new AssetSnapshot(
                LocalDate.of(2026, 10, 31),
                new BigDecimal("240100"),
                new BigDecimal("96772"),
                BigDecimal.ZERO,
                new BigDecimal("136648"),
                new BigDecimal("6680"));
        when(lineIdTokenVerifier.verify("valid-token")).thenReturn(Optional.of("U123"));
        when(lineUserLinkService.findByLineUserId("U123")).thenReturn(Optional.of(link));
        when(assetSnapshotService.findRecentByUserId(7L)).thenReturn(List.of(snapshot));

        ResponseEntity<Map<String, Object>> response = controller.getSnapshots("Bearer valid-token");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("ok", true);
        assertThat(response.getBody()).containsEntry("snapshots", List.of(snapshot));
        verify(assetSnapshotService).findRecentByUserId(7L);
    }

    @Test
    void rejectsRequestsWhenLineUserIsNotLinked() {
        when(lineIdTokenVerifier.verify(anyString())).thenReturn(Optional.of("U123"));
        when(lineUserLinkService.findByLineUserId("U123")).thenReturn(Optional.empty());

        ResponseEntity<Map<String, Object>> response = controller.getSnapshots("Bearer valid-token");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}