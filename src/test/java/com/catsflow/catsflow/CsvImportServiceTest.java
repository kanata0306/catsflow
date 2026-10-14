package com.catsflow.catsflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.catsflow.catsflow.Service.CsvImportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class CsvImportServiceTest {

    @Autowired
    private CsvImportService csvImportService;

    @Test
    void detectsTransactionCsvAndValidatesRequiredColumns() {
        String csv = "日付,内容,金額,大項目,中項目\n"
                + "2026-10-01,スーパー,3500,食費,食料品\n"
                + "2026-10-02,給与,250000,収入,給与\n";

        var result = csvImportService.validateCsv("transactions.csv", csv);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getType()).isEqualTo("transactions");
        assertThat(result.getRowCount()).isEqualTo(2);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    void rejectsCsvMissingRequiredColumns() {
        String csv = "日付,内容\n2026-10-01,テスト\n";

        var result = csvImportService.validateCsv("broken.csv", csv);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getType()).isEqualTo("unknown");
        assertThat(result.getErrors()).isNotEmpty();
    }

    @Test
    void importsTransactionCsvAndCreatesSyncLog() {
        String csv = "日付,内容,金額,大項目,中項目\n"
                + "2026-10-05,スーパー,3500,食費,食料品\n"
                + "2026-10-06,給与,250000,収入,給与\n";

        var result = csvImportService.importTransactionsCsv(1L, "transactions.csv", csv);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getImportedCount()).isEqualTo(2);
        assertThat(result.getErrorMessages()).isEmpty();
    }
}
