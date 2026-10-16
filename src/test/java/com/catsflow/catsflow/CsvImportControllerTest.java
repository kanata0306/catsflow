package com.catsflow.catsflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.catsflow.catsflow.Controller.CsvImportController;
import com.catsflow.catsflow.Entity.LineUserLink;
import com.catsflow.catsflow.Service.CsvImportService;
import com.catsflow.catsflow.Service.CsvImportService.CsvImportResult;
import com.catsflow.catsflow.Service.CsvImportService.CsvValidationResult;
import com.catsflow.catsflow.Service.LineIdTokenVerifier;
import com.catsflow.catsflow.Service.LineUserLinkService;
import java.nio.charset.StandardCharsets;
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
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class CsvImportControllerTest {

    @Mock
    private CsvImportService csvImportService;

    @Mock
    private LineIdTokenVerifier lineIdTokenVerifier;

    @Mock
    private LineUserLinkService lineUserLinkService;

    @InjectMocks
    private CsvImportController controller;

    @Test
    void returnsUnauthorizedWhenNoAuthorizationHeader() {
        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv",
                "日付,内容\n2026/10/01,テスト\n".getBytes(StandardCharsets.UTF_8));

        ResponseEntity<Map<String, Object>> response = controller.importCsv(null, file);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void returnsUnauthorizedWhenTokenVerificationFails() {
        when(lineIdTokenVerifier.verify(anyString())).thenReturn(Optional.empty());
        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv",
                "日付,内容\n2026/10/01,テスト\n".getBytes(StandardCharsets.UTF_8));

        ResponseEntity<Map<String, Object>> response = controller.importCsv("Bearer invalid-token", file);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void returnsUnauthorizedWhenUserNotLinked() {
        when(lineIdTokenVerifier.verify(anyString())).thenReturn(Optional.of("U1234567890"));
        when(lineUserLinkService.findByLineUserId(anyString())).thenReturn(Optional.empty());
        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv",
                "日付,内容\n2026/10/01,テスト\n".getBytes(StandardCharsets.UTF_8));

        ResponseEntity<Map<String, Object>> response = controller.importCsv("Bearer valid-token", file);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void returnsBadRequestWhenFileIsEmpty() {
        setupAuthenticatedUser();
        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", new byte[0]);

        ResponseEntity<Map<String, Object>> response = controller.importCsv("Bearer valid-token", file);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("message", "ファイルが選択されていません。");
    }

    @Test
    void returnsBadRequestWhenFileIsNotCsv() {
        setupAuthenticatedUser();
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain",
                "test".getBytes(StandardCharsets.UTF_8));

        ResponseEntity<Map<String, Object>> response = controller.importCsv("Bearer valid-token", file);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("message", "CSVファイルを選択してください。");
    }

    @Test
    void returnsBadRequestWhenValidationFails() {
        setupAuthenticatedUser();
        when(csvImportService.validateCsv(anyString(), anyString()))
                .thenReturn(new CsvValidationResult("unknown", 0, false, List.of("CSV種別を判定できませんでした")));
        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv",
                "invalid,csv\n".getBytes(StandardCharsets.UTF_8));

        ResponseEntity<Map<String, Object>> response = controller.importCsv("Bearer valid-token", file);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("ok", false);
    }

    @Test
    void importsTransactionsCsvSuccessfully() {
        setupAuthenticatedUser();
        when(csvImportService.validateCsv(anyString(), anyString()))
                .thenReturn(new CsvValidationResult("transactions", 2, true, List.of()));
        when(csvImportService.importTransactionsCsv(any(), anyString(), anyString()))
                .thenReturn(new CsvImportResult(true, 2, 0, 0, List.of()));
        MockMultipartFile file = new MockMultipartFile("file", "transactions.csv", "text/csv",
                "計算対象,日付,内容,金額（円）\n1,2026/10/01,テスト,-100\n".getBytes(StandardCharsets.UTF_8));

        ResponseEntity<Map<String, Object>> response = controller.importCsv("Bearer valid-token", file);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("ok", true);
        assertThat(response.getBody()).containsEntry("type", "transactions");
        assertThat(response.getBody()).containsEntry("importedCount", 2);
    }

    @Test
    void importsAssetsCsvSuccessfully() {
        setupAuthenticatedUser();
        when(csvImportService.validateCsv(anyString(), anyString()))
                .thenReturn(new CsvValidationResult("assets", 1, true, List.of()));
        when(csvImportService.importAssetsCsv(any(), anyString(), anyString()))
                .thenReturn(new CsvImportResult(true, 1, 0, 0, List.of()));
        MockMultipartFile file = new MockMultipartFile("file", "assets.csv", "text/csv",
                "日付,合計（円）\n2026/10/17,240100\n".getBytes(StandardCharsets.UTF_8));

        ResponseEntity<Map<String, Object>> response = controller.importCsv("Bearer valid-token", file);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("ok", true);
        assertThat(response.getBody()).containsEntry("type", "assets");
    }

    @Test
    void returnsUnprocessableEntityWhenImportFails() {
        setupAuthenticatedUser();
        when(csvImportService.validateCsv(anyString(), anyString()))
                .thenReturn(new CsvValidationResult("transactions", 1, true, List.of()));
        when(csvImportService.importTransactionsCsv(any(), anyString(), anyString()))
                .thenReturn(new CsvImportResult(false, 0, 0, 0, List.of("行 1 の取り込みに失敗しました")));
        MockMultipartFile file = new MockMultipartFile("file", "transactions.csv", "text/csv",
                "計算対象,日付,内容,金額（円）\n1,2026/10/01,テスト,-100\n".getBytes(StandardCharsets.UTF_8));

        ResponseEntity<Map<String, Object>> response = controller.importCsv("Bearer valid-token", file);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).containsEntry("ok", false);
    }

    private void setupAuthenticatedUser() {
        when(lineIdTokenVerifier.verify(anyString())).thenReturn(Optional.of("U1234567890"));
        LineUserLink link = new LineUserLink();
        link.setUserId(1L);
        link.setLineUserId("U1234567890");
        when(lineUserLinkService.findByLineUserId(anyString())).thenReturn(Optional.of(link));
    }
}
