package com.catsflow.catsflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.catsflow.catsflow.Service.AssetSnapshotService;
import com.catsflow.catsflow.Service.CsvImportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
class AssetSnapshotServiceTest {

    @Autowired
    private AssetSnapshotService assetSnapshotService;

    @Autowired
    private CsvImportService csvImportService;

    @Test
    @Transactional
    void returnsImportedSnapshotsNewestFirst() {
        String csv = "日付,合計（円）,預金・現金（円）,投資信託（円）\n"
                + "2026/11/02,250000,100000,150000\n"
                + "2026/11/01,240000,95000,145000\n";

        var importResult = csvImportService.importAssetsCsv(1L, "assets-history.csv", csv);
        var snapshots = assetSnapshotService.findRecentByUserId(1L);

        assertThat(importResult.isSuccess()).isTrue();
        assertThat(importResult.getImportedCount()).isEqualTo(2);
        assertThat(snapshots).first().satisfies(snapshot -> {
            assertThat(snapshot.snapshotDate()).hasToString("2026-11-02");
            assertThat(snapshot.totalAmount()).isEqualByComparingTo("250000");
            assertThat(snapshot.cashAmount()).isEqualByComparingTo("100000");
            assertThat(snapshot.investmentTrustAmount()).isEqualByComparingTo("150000");
        });
    }
}