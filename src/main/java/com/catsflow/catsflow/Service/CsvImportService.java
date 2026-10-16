package com.catsflow.catsflow.Service;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

@Service
public class CsvImportService {

    private final JdbcTemplate jdbcTemplate;

    public CsvImportService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * バイト列からCSV内容を文字コード自動判定でデコードする。
     * UTF-8（BOMあり/なし）を優先し、不正な場合はShift-JISとみなす。
     */
    public static String decodeCsv(byte[] csvBytes) {
        if (csvBytes == null || csvBytes.length == 0) {
            return "";
        }
        if (isValidUtf8(csvBytes)) {
            return new String(csvBytes, StandardCharsets.UTF_8);
        }
        return new String(csvBytes, Charset.forName("Shift_JIS"));
    }

    private static boolean isValidUtf8(byte[] bytes) {
        // BOM付きUTF-8
        if (bytes.length >= 3 && bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF) {
            return true;
        }
        // UTF-8としてデコード可能か検証
        var decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT);
        try {
            decoder.decode(java.nio.ByteBuffer.wrap(bytes));
            return true;
        } catch (java.nio.charset.CharacterCodingException e) {
            return false;
        }
    }

    public CsvValidationResult validateCsv(String fileName, String csvContent) {
        List<String> errors = new ArrayList<>();

        if (fileName == null || fileName.isBlank()) {
            errors.add("ファイル名が未指定です");
        } else if (!fileName.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            errors.add("CSVファイルではありません");
        }

        if (csvContent == null || csvContent.isBlank()) {
            errors.add("CSV内容が空です");
            return new CsvValidationResult("unknown", 0, false, errors);
        }

        String normalized = csvContent.replace("\r\n", "\n").replace("\r", "\n");
        String[] rows = normalized.split("\n", -1);
        if (rows.length < 2) {
            errors.add("ヘッダー行とデータ行が必要です");
            return new CsvValidationResult("unknown", 0, false, errors);
        }

        List<String> header = parseCsvLine(rows[0]);
        String csvType = detectCsvType(header);
        if ("unknown".equals(csvType)) {
            errors.add("CSV種別を判定できませんでした");
            return new CsvValidationResult(csvType, 0, false, errors);
        }

        Set<String> requiredColumns = requiredColumnsFor(csvType);
        Set<String> headerSet = new HashSet<>();
        for (String column : header) {
            headerSet.add(column.trim());
        }

        for (String requiredColumn : requiredColumns) {
            if (!headerSet.contains(requiredColumn)) {
                errors.add("必須列が不足しています: " + requiredColumn);
            }
        }

        int dataRowCount = 0;
        for (int i = 1; i < rows.length; i++) {
            if (!rows[i].isBlank()) {
                dataRowCount++;
            }
        }

        return new CsvValidationResult(csvType, dataRowCount, errors.isEmpty(), errors);
    }

    public CsvImportResult importTransactionsCsv(Long userId, String fileName, String csvContent) {
        if (userId == null) {
            return new CsvImportResult(false, 0, 0, 0, List.of("ユーザーIDが未指定です"));
        }

        CsvValidationResult validation = validateCsv(fileName, csvContent);
        if (!validation.isValid()) {
            return new CsvImportResult(false, 0, 0, 0, validation.getErrors());
        }

        if (!"transactions".equals(validation.getType())) {
            return new CsvImportResult(false, 0, 0, 0, List.of("入出金CSVではありません"));
        }

        Long accountId = findDefaultAccountId(userId);
        if (accountId == null) {
            return new CsvImportResult(false, 0, 0, 0, List.of("取り込み先の口座が見つかりませんでした"));
        }

        String normalized = csvContent.replace("\r\n", "\n").replace("\r", "\n");
        String[] rows = normalized.split("\n", -1);
        List<String> header = parseCsvLine(rows[0]);
        Map<String, Integer> columns = indexHeader(header);

        Long importFileId = insertImportFile(userId, fileName, rows.length - 1);
        int importedCount = 0;
        int skippedDuplicateCount = 0;
        int skippedExcludedCount = 0;
        List<String> errors = new ArrayList<>();

        for (int i = 1; i < rows.length; i++) {
            if (rows[i].isBlank()) {
                continue;
            }

            List<String> rowValues = parseCsvLine(rows[i]);
            String dateText = valueFor(rowValues, columns, "日付");
            String content = valueFor(rowValues, columns, "内容");
            String amountText = findValue(rowValues, columns, "金額（円）", "金額");
            String categoryName = valueFor(rowValues, columns, "大項目");
            String externalId = valueFor(rowValues, columns, "ID");
            String transferFlag = valueFor(rowValues, columns, "振替");
            String calcTargetFlag = valueFor(rowValues, columns, "計算対象");

            if (categoryName == null || categoryName.isBlank()) {
                categoryName = valueFor(rowValues, columns, "中項目");
            }
            if (categoryName == null || categoryName.isBlank()) {
                categoryName = "未分類";
            }

            if (dateText.isBlank() || content.isBlank() || amountText.isBlank()) {
                errors.add("行 " + i + " で必須項目が不足しています");
                continue;
            }

            // 重複チェック：外部IDが存在する場合はスキップ
            if (!externalId.isBlank() && existsByExternalId(userId, externalId)) {
                skippedDuplicateCount++;
                continue;
            }

            try {
                LocalDate transactionDate = parseDate(dateText);
                BigDecimal amount = new BigDecimal(amountText.replace(",", "").trim());
                String normalizedType = amount.compareTo(BigDecimal.ZERO) >= 0 ? "income" : "expense";
                BigDecimal storedAmount = normalizedType.equals("income") ? amount : amount.abs().negate();
                boolean isTransfer = "1".equals(transferFlag);
                boolean isCalcTarget = !"0".equals(calcTargetFlag);

                Long categoryId = findOrCreateCategory(userId, categoryName, normalizedType);
                jdbcTemplate.update(
                        "INSERT INTO transactions (user_id, account_id, category_id, transaction_date, amount, type, description, memo, external_id, is_transfer, is_calculation_target, import_file_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        userId,
                        accountId,
                        categoryId,
                        transactionDate,
                        storedAmount,
                        normalizedType,
                        content,
                        "CSV取込",
                        externalId.isBlank() ? null : externalId,
                        isTransfer,
                        isCalcTarget,
                        importFileId);
                importedCount++;
                if (!isCalcTarget || isTransfer) {
                    skippedExcludedCount++;
                }
            } catch (Exception ex) {
                errors.add("行 " + i + " の取り込みに失敗しました: " + ex.getMessage());
            }
        }

        writeSyncLog(userId, "csv", fileName, importedCount, errors.isEmpty() ? "success" : "warning",
                errors.isEmpty() ? "CSVを取り込みました" : String.join("; ", errors));
        return new CsvImportResult(errors.isEmpty(), importedCount, skippedDuplicateCount, skippedExcludedCount,
                errors);
    }

    private boolean existsByExternalId(Long userId, String externalId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE user_id = ? AND external_id = ?",
                Integer.class,
                userId,
                externalId);
        return count != null && count > 0;
    }

    /**
     * 資産推移CSVを取り込み、基準日ごとの資産スナップショットを保存する。
     * 同一基準日のデータが既に存在する場合は上書き（加算しない）。
     */
    public CsvImportResult importAssetsCsv(Long userId, String fileName, String csvContent) {
        if (userId == null) {
            return new CsvImportResult(false, 0, 0, 0, List.of("ユーザーIDが未指定です"));
        }

        CsvValidationResult validation = validateCsv(fileName, csvContent);
        if (!validation.isValid()) {
            return new CsvImportResult(false, 0, 0, 0, validation.getErrors());
        }

        if (!"assets".equals(validation.getType())) {
            return new CsvImportResult(false, 0, 0, 0, List.of("資産推移CSVではありません"));
        }

        String normalized = csvContent.replace("\r\n", "\n").replace("\r", "\n");
        String[] rows = normalized.split("\n", -1);
        List<String> header = parseCsvLine(rows[0]);
        Map<String, Integer> columns = indexHeader(header);

        Long importFileId = insertImportFile(userId, fileName, rows.length - 1);
        int importedCount = 0;
        int updatedCount = 0;
        List<String> errors = new ArrayList<>();

        for (int i = 1; i < rows.length; i++) {
            if (rows[i].isBlank()) {
                continue;
            }

            List<String> rowValues = parseCsvLine(rows[i]);
            String dateText = valueFor(rowValues, columns, "日付");
            String totalText = findValue(rowValues, columns, "合計（円）", "合計");

            if (dateText.isBlank() || totalText.isBlank()) {
                errors.add("行 " + i + " で必須項目が不足しています");
                continue;
            }

            try {
                LocalDate snapshotDate = parseDate(dateText);
                BigDecimal totalAmount = new BigDecimal(totalText.replace(",", "").trim());
                BigDecimal cashAmount = parseAmountOrNull(findValue(rowValues, columns, "預金・現金（円）", "預金・現金"));
                BigDecimal stockAmount = parseAmountOrNull(findValue(rowValues, columns, "株式(現物)（円）", "株式(現物)", "株式"));
                BigDecimal investmentTrustAmount = parseAmountOrNull(findValue(rowValues, columns, "投資信託（円）", "投資信託"));
                BigDecimal pointAmount = parseAmountOrNull(findValue(rowValues, columns, "ポイント（円）", "ポイント"));

                boolean exists = existsAssetSnapshot(userId, snapshotDate);
                if (exists) {
                    jdbcTemplate.update(
                            "UPDATE asset_snapshots SET total_amount = ?, cash_amount = ?, stock_amount = ?, investment_trust_amount = ?, point_amount = ?, import_file_id = ?, updated_at = CURRENT_TIMESTAMP WHERE user_id = ? AND snapshot_date = ?",
                            totalAmount, cashAmount, stockAmount, investmentTrustAmount, pointAmount,
                            importFileId, userId, snapshotDate);
                    updatedCount++;
                } else {
                    jdbcTemplate.update(
                            "INSERT INTO asset_snapshots (user_id, snapshot_date, total_amount, cash_amount, stock_amount, investment_trust_amount, point_amount, source, import_file_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                            userId, snapshotDate, totalAmount, cashAmount, stockAmount, investmentTrustAmount,
                            pointAmount, "csv", importFileId);
                    importedCount++;
                }
            } catch (Exception ex) {
                errors.add("行 " + i + " の取り込みに失敗しました: " + ex.getMessage());
            }
        }

        writeSyncLog(userId, "csv-assets", fileName, importedCount + updatedCount,
                errors.isEmpty() ? "success" : "warning",
                errors.isEmpty()
                        ? "資産推移CSVを取り込みました（新規" + importedCount + "件、更新" + updatedCount + "件）"
                        : String.join("; ", errors));
        return new CsvImportResult(errors.isEmpty(), importedCount, 0, updatedCount, errors);
    }

    private boolean existsAssetSnapshot(Long userId, LocalDate snapshotDate) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM asset_snapshots WHERE user_id = ? AND snapshot_date = ?",
                Integer.class,
                userId,
                snapshotDate);
        return count != null && count > 0;
    }

    private BigDecimal parseAmountOrNull(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return new BigDecimal(text.replace(",", "").trim());
    }

    private LocalDate parseDate(String dateText) {
        String trimmed = dateText.trim();
        if (trimmed.contains("/")) {
            return LocalDate.parse(trimmed, DateTimeFormatter.ofPattern("yyyy/M/d"));
        }
        return LocalDate.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE);
    }

    private String findValue(List<String> rowValues, Map<String, Integer> columns, String... columnNames) {
        for (String columnName : columnNames) {
            Integer index = columns.get(columnName);
            if (index != null && index < rowValues.size()) {
                return rowValues.get(index).trim();
            }
        }
        return "";
    }

    private Long findDefaultAccountId(Long userId) {
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM accounts WHERE user_id = ? ORDER BY id LIMIT 1",
                Long.class,
                userId);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private Long findOrCreateCategory(Long userId, String categoryName, String type) {
        String rawName = categoryName == null ? "未分類" : categoryName.trim();
        String normalizedName = rawName.isBlank() ? "未分類" : rawName;
        final String categoryNameForInsert = normalizedName;

        List<Long> categoryIds = jdbcTemplate.queryForList(
                "SELECT id FROM categories WHERE user_id = ? AND name = ? AND type = ? LIMIT 1",
                Long.class,
                userId,
                normalizedName,
                type);
        if (!categoryIds.isEmpty()) {
            return categoryIds.get(0);
        }

        Long nextCategoryId = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(id), 0) + 1 FROM categories",
                Long.class);
        jdbcTemplate.update(
                "INSERT INTO categories (id, user_id, name, type) VALUES (?, ?, ?, ?)",
                nextCategoryId,
                userId,
                categoryNameForInsert,
                type);
        return nextCategoryId;
    }

    private Long insertImportFile(Long userId, String fileName, int rowCount) {
        Long nextImportFileId = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(id), 0) + 1 FROM import_files",
                Long.class);
        jdbcTemplate.update(
                "INSERT INTO import_files (id, user_id, file_name, record_count) VALUES (?, ?, ?, ?)",
                nextImportFileId,
                userId,
                fileName,
                rowCount);
        return nextImportFileId;
    }

    private void writeSyncLog(Long userId, String sourceType, String sourceName, int recordCount, String status,
            String message) {
        String safeMessage = message == null ? "" : message;
        if (safeMessage.length() > 500) {
            safeMessage = safeMessage.substring(0, 500);
        }

        jdbcTemplate.update(
                "INSERT INTO sync_logs (user_id, source_type, source_name, status, record_count, message) VALUES (?, ?, ?, ?, ?, ?)",
                userId,
                sourceType,
                sourceName,
                status,
                recordCount,
                safeMessage);
    }

    private Map<String, Integer> indexHeader(List<String> header) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            map.put(header.get(i).trim(), i);
        }
        return map;
    }

    private String valueFor(List<String> rowValues, Map<String, Integer> columns, String columnName) {
        Integer index = columns.get(columnName);
        if (index == null || index >= rowValues.size()) {
            return "";
        }
        return rowValues.get(index).trim();
    }

    private String detectCsvType(List<String> header) {
        Set<String> normalizedHeader = new HashSet<>();
        for (String column : header) {
            normalizedHeader.add(column.trim());
        }

        boolean hasDate = normalizedHeader.contains("日付");
        boolean hasContent = normalizedHeader.contains("内容");
        boolean hasAmount = normalizedHeader.contains("金額") || normalizedHeader.contains("金額（円）");
        boolean hasTotal = normalizedHeader.contains("合計") || normalizedHeader.contains("合計（円）");

        if (hasDate && hasContent && hasAmount) {
            return "transactions";
        }
        if (hasDate && hasTotal) {
            return "assets";
        }
        return "unknown";
    }

    private Set<String> requiredColumnsFor(String csvType) {
        if ("transactions".equals(csvType)) {
            return Set.of("日付", "内容");
        }
        if ("assets".equals(csvType)) {
            return Set.of("日付");
        }
        return Set.of();
    }

    private List<String> parseCsvLine(String line) {
        List<String> columns = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (ch == ',' && !inQuotes) {
                columns.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        columns.add(current.toString());
        return columns;
    }

    public static class CsvValidationResult {
        private final String type;
        private final int rowCount;
        private final boolean valid;
        private final List<String> errors;

        public CsvValidationResult(String type, int rowCount, boolean valid, List<String> errors) {
            this.type = type;
            this.rowCount = rowCount;
            this.valid = valid;
            this.errors = errors == null ? List.of() : List.copyOf(errors);
        }

        public String getType() {
            return type;
        }

        public int getRowCount() {
            return rowCount;
        }

        public boolean isValid() {
            return valid;
        }

        public List<String> getErrors() {
            return errors;
        }
    }

    public static class CsvImportResult {
        private final boolean success;
        private final int importedCount;
        private final int skippedDuplicateCount;
        private final int skippedExcludedCount;
        private final List<String> errorMessages;

        public CsvImportResult(boolean success, int importedCount, int skippedDuplicateCount,
                int skippedExcludedCount, List<String> errorMessages) {
            this.success = success;
            this.importedCount = importedCount;
            this.skippedDuplicateCount = skippedDuplicateCount;
            this.skippedExcludedCount = skippedExcludedCount;
            this.errorMessages = errorMessages == null ? List.of() : List.copyOf(errorMessages);
        }

        public boolean isSuccess() {
            return success;
        }

        public int getImportedCount() {
            return importedCount;
        }

        public int getSkippedDuplicateCount() {
            return skippedDuplicateCount;
        }

        public int getSkippedExcludedCount() {
            return skippedExcludedCount;
        }

        public List<String> getErrorMessages() {
            return errorMessages;
        }
    }
}
