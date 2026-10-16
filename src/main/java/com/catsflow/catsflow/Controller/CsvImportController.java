package com.catsflow.catsflow.Controller;

import com.catsflow.catsflow.Entity.LineUserLink;
import com.catsflow.catsflow.Service.CsvImportService;
import com.catsflow.catsflow.Service.CsvImportService.CsvImportResult;
import com.catsflow.catsflow.Service.LineIdTokenVerifier;
import com.catsflow.catsflow.Service.LineUserLinkService;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class CsvImportController {

    private final CsvImportService csvImportService;
    private final LineIdTokenVerifier lineIdTokenVerifier;
    private final LineUserLinkService lineUserLinkService;

    public CsvImportController(CsvImportService csvImportService,
            LineIdTokenVerifier lineIdTokenVerifier,
            LineUserLinkService lineUserLinkService) {
        this.csvImportService = csvImportService;
        this.lineIdTokenVerifier = lineIdTokenVerifier;
        this.lineUserLinkService = lineUserLinkService;
    }

    /**
     * Mini AppからCSVファイルをアップロードして取り込む。
     * LINE IDトークンで認証し、連携済みユーザーのデータとして保存する。
     */
    @PostMapping("/api/import/csv")
    public ResponseEntity<Map<String, Object>> importCsv(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam("file") MultipartFile file) {

        // 認証：LINE IDトークンを検証
        Optional<Long> userIdOpt = authenticate(authorization);
        if (userIdOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("ok", false, "message", "認証に失敗しました。LINEログインを確認してください。"));
        }
        Long userId = userIdOpt.get();

        // ファイルの基本検証
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("ok", false, "message", "ファイルが選択されていません。"));
        }

        String fileName = file.getOriginalFilename();
        if (fileName == null || !fileName.toLowerCase(java.util.Locale.ROOT).endsWith(".csv")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("ok", false, "message", "CSVファイルを選択してください。"));
        }

        // ファイルサイズ制限（10MB）
        if (file.getSize() > 10 * 1024 * 1024) {
            return ResponseEntity.badRequest()
                    .body(Map.of("ok", false, "message", "ファイルサイズが大きすぎます（10MB以下にしてください）。"));
        }

        try {
            // バイト列を文字コード自動判定でデコード
            String csvContent = CsvImportService.decodeCsv(file.getBytes());

            // CSV種別を判定
            var validation = csvImportService.validateCsv(fileName, csvContent);
            if (!validation.isValid()) {
                return ResponseEntity.badRequest()
                        .body(Map.of(
                                "ok", false,
                                "message", "CSVの形式が正しくありません。",
                                "errors", validation.getErrors()));
            }

            // 種別に応じて取り込み処理を実行
            CsvImportResult result;
            if ("transactions".equals(validation.getType())) {
                result = csvImportService.importTransactionsCsv(userId, fileName, csvContent);
            } else if ("assets".equals(validation.getType())) {
                result = csvImportService.importAssetsCsv(userId, fileName, csvContent);
            } else {
                return ResponseEntity.badRequest()
                        .body(Map.of("ok", false, "message", "対応していないCSV形式です。"));
            }

            if (!result.isSuccess()) {
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                        .body(Map.of(
                                "ok", false,
                                "message", "取り込みに失敗しました。",
                                "errors", result.getErrorMessages()));
            }

            // 成功レスポンス
            return ResponseEntity.ok(Map.of(
                    "ok", true,
                    "message", "CSVを取り込みました。",
                    "type", validation.getType(),
                    "importedCount", result.getImportedCount(),
                    "skippedDuplicateCount", result.getSkippedDuplicateCount(),
                    "skippedExcludedCount", result.getSkippedExcludedCount()));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("ok", false, "message", "取り込み処理でエラーが発生しました。"));
        }
    }

    /**
     * AuthorizationヘッダーのBearerトークン（LINE IDトークン）を検証し、
     * 連携済みのCatsflowユーザーIDを返す。
     */
    private Optional<Long> authenticate(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return Optional.empty();
        }

        String idToken = authorization.substring(7);
        Optional<String> lineUserIdOpt = lineIdTokenVerifier.verify(idToken);
        if (lineUserIdOpt.isEmpty()) {
            return Optional.empty();
        }

        Optional<LineUserLink> linkOpt = lineUserLinkService.findByLineUserId(lineUserIdOpt.get());
        return linkOpt.map(LineUserLink::getUserId);
    }
}
