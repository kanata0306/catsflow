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
        String csv = "計算対象,日付,内容,金額（円）,保有金融機関,大項目,中項目,メモ,振替,ID\n"
                + "1,2026/10/01,スーパー,-3500,現金,食費,食料品,,0,EXT001\n"
                + "1,2026/10/02,給与,250000,みずほ銀行,収入,給与,,0,EXT002\n";

        var result = csvImportService.validateCsv("transactions.csv", csv);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getType()).isEqualTo("transactions");
        assertThat(result.getRowCount()).isEqualTo(2);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    void detectsAssetsCsv() {
        String csv = "日付,合計（円）,預金・現金（円）\n"
                + "2026/10/17,240100,96772\n";

        var result = csvImportService.validateCsv("assets.csv", csv);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getType()).isEqualTo("assets");
    }

    @Test
    void rejectsCsvMissingRequiredColumns() {
        String csv = "内容,金額\n2026-10-01,テスト\n";

        var result = csvImportService.validateCsv("broken.csv", csv);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getType()).isEqualTo("unknown");
        assertThat(result.getErrors()).isNotEmpty();
    }

    @Test
    void importsTransactionCsvAndCreatesSyncLog() {
        String csv = "計算対象,日付,内容,金額（円）,保有金融機関,大項目,中項目,メモ,振替,ID\n"
                + "1,2026/10/05,スーパー,-3500,現金,食費,食料品,,0,TEST_IMP_001\n"
                + "1,2026/10/06,給与,250000,みずほ銀行,収入,給与,,0,TEST_IMP_002\n";

        var result = csvImportService.importTransactionsCsv(1L, "transactions.csv", csv);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getImportedCount()).isEqualTo(2);
        assertThat(result.getSkippedDuplicateCount()).isEqualTo(0);
        assertThat(result.getErrorMessages()).isEmpty();
    }

    @Test
    void skipsDuplicateExternalIdOnReimport() {
        String csv = "計算対象,日付,内容,金額（円）,保有金融機関,大項目,中項目,メモ,振替,ID\n"
                + "1,2026/10/05,スーパー,-3500,現金,食費,食料品,,0,TEST_DUP_001\n";

        csvImportService.importTransactionsCsv(1L, "first.csv", csv);
        var secondResult = csvImportService.importTransactionsCsv(1L, "second.csv", csv);

        assertThat(secondResult.isSuccess()).isTrue();
        assertThat(secondResult.getImportedCount()).isEqualTo(0);
        assertThat(secondResult.getSkippedDuplicateCount()).isEqualTo(1);
    }

    @Test
    void storesTransferAndCalcTargetFlags() {
        String csv = "計算対象,日付,内容,金額（円）,保有金融機関,大項目,中項目,メモ,振替,ID\n"
                + "0,2026/10/10,AMAZON.CO.JP,-598,三井住友カード,衣服・美容,衣服,,1,TEST_FLAG_001\n"
                + "1,2026/10/15,鉄道利用,-290,ICOCA,交通費,電車,,0,TEST_FLAG_002\n";

        var result = csvImportService.importTransactionsCsv(1L, "flags.csv", csv);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getImportedCount()).isEqualTo(2);
        assertThat(result.getSkippedExcludedCount()).isEqualTo(1);
    }

    @Test
    void decodesShiftJisCsvBytes() {
        String csvText = "計算対象,日付,内容,金額（円）\n1,2026/10/01,テスト,-100\n";
        byte[] sjisBytes = csvText.getBytes(java.nio.charset.Charset.forName("Shift_JIS"));

        String decoded = CsvImportService.decodeCsv(sjisBytes);

        assertThat(decoded).contains("テスト");
    }

    @Test
    void decodesUtf8CsvBytes() {
        String csvText = "計算対象,日付,内容,金額（円）\n1,2026/10/01,テスト,-100\n";
        byte[] utf8Bytes = csvText.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        String decoded = CsvImportService.decodeCsv(utf8Bytes);

        assertThat(decoded).contains("テスト");
    }

    @Test
    void importsAssetsCsvAndCreatesSnapshots() {
        String csv = "日付,合計（円）,預金・現金（円）,株式(現物)（円）,投資信託（円）,ポイント（円）\n"
                + "2026/10/17,240100,96772,0,136648,6680\n"
                + "2026/10/16,240100,96772,0,136648,6680\n"
                + "2026/10/15,242060,98732,0,136648,6680\n";

        var result = csvImportService.importAssetsCsv(1L, "assets.csv", csv);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getImportedCount()).isEqualTo(3);
        assertThat(result.getSkippedExcludedCount()).isEqualTo(0);
        assertThat(result.getErrorMessages()).isEmpty();
    }

    @Test
    void updatesExistingSnapshotOnSameDate() {
        String csv1 = "日付,合計（円）,預金・現金（円）,投資信託（円）\n"
                + "2026/10/17,240100,96772,136648\n";
        String csv2 = "日付,合計（円）,預金・現金（円）,投資信託（円）\n"
                + "2026/10/17,250000,100000,140000\n";

        csvImportService.importAssetsCsv(1L, "assets1.csv", csv1);
        var result2 = csvImportService.importAssetsCsv(1L, "assets2.csv", csv2);

        assertThat(result2.isSuccess()).isTrue();
        assertThat(result2.getImportedCount()).isEqualTo(0);
        assertThat(result2.getSkippedExcludedCount()).isEqualTo(1);
    }

    @Test
    void rejectsTransactionCsvForAssetsImport() {
        String csv = "計算対象,日付,内容,金額（円）,保有金融機関,大項目,中項目,メモ,振替,ID\n"
                + "1,2026/10/01,スーパー,-3500,現金,食費,食料品,,0,EXT001\n";

        var result = csvImportService.importAssetsCsv(1L, "transactions.csv", csv);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessages()).contains("資産推移CSVではありません");
    }
}
