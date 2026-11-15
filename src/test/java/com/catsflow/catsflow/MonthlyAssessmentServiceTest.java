package com.catsflow.catsflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.catsflow.catsflow.Repository.TransactionRepository;
import com.catsflow.catsflow.Service.AssetSnapshotService;
import com.catsflow.catsflow.Service.MonthlyAssessmentService;
import com.catsflow.catsflow.Service.PlanningDataService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MonthlyAssessmentServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AssetSnapshotService assetSnapshotService;

    @Mock
    private PlanningDataService planningDataService;

    private MonthlyAssessmentService service;

    @BeforeEach
    void setUp() {
        service = new MonthlyAssessmentService(transactionRepository, assetSnapshotService, planningDataService);
    }

    @Test
    void evaluatesStoredFactsWithoutSendingDataExternally() {
        YearMonth month = YearMonth.of(2026, 10);
        LocalDate periodStart = LocalDate.of(2026, 5, 1);
        LocalDate periodEnd = LocalDate.of(2026, 10, 31);
        when(transactionRepository.findByUserIdBetweenDates(7L, periodStart, periodEnd)).thenReturn(List.of(
                Map.of("type", "income", "amount", new BigDecimal("300000"),
                        "transaction_date", java.sql.Date.valueOf("2026-10-01")),
                Map.of("type", "expense", "amount", new BigDecimal("-100000"), "category_name", "食費",
                        "transaction_date", java.sql.Date.valueOf("2026-10-02")),
                Map.of("type", "expense", "amount", new BigDecimal("-50000"), "category_name", "住居費",
                        "transaction_date", java.sql.Date.valueOf("2026-10-03")),
                Map.of("type", "expense", "amount", new BigDecimal("-20000"), "category_name", "食費",
                        "transaction_date", java.sql.Date.valueOf("2026-06-15"))));
        when(assetSnapshotService.findRecentByUserId(7L)).thenReturn(List.of(
                new AssetSnapshotService.AssetSnapshot(LocalDate.of(2026, 10, 31), new BigDecimal("900000"),
                        new BigDecimal("500000"), BigDecimal.ZERO, new BigDecimal("390000"), new BigDecimal("10000")),
                new AssetSnapshotService.AssetSnapshot(LocalDate.of(2026, 5, 31), new BigDecimal("850000"),
                        null, null, null, null)));
        when(planningDataService.findLiabilities(7L)).thenReturn(List.of(
                new PlanningDataService.LongTermLiability(1L, "奨学金", "student_loan", new BigDecimal("200000"),
                        LocalDate.of(2026, 10, 31), null, null, null, null, LocalDateTime.now(), LocalDateTime.now())));
        when(planningDataService.findGoals(7L)).thenReturn(List.of(
                new PlanningDataService.FinancialGoal(2L, "savings", "緊急資金", new BigDecimal("500000"),
                        new BigDecimal("250000"), "2027-10", new BigDecimal("20000"), "active",
                        LocalDateTime.now(), LocalDateTime.now())));

        var result = service.evaluate(7L, month);

        assertThat(result.startMonth()).isEqualTo("2026-05");
        assertThat(result.endMonth()).isEqualTo("2026-10");
        assertThat(result.periodMonths()).isEqualTo(6);
        assertThat(result.assessmentMode()).isEqualTo("rule_based");
        assertThat(result.aiStatus()).isEqualTo("not_configured");
        assertThat(result.externalDataSent()).isFalse();
        assertThat(result.figures().income()).isEqualByComparingTo("300000");
        assertThat(result.figures().expense()).isEqualByComparingTo("170000");
        assertThat(result.figures().balance()).isEqualByComparingTo("130000");
        assertThat(result.figures().balanceRatePercent()).isEqualByComparingTo("43.3");
        assertThat(result.liabilities().netWorth()).isEqualByComparingTo("700000");
        assertThat(result.topCategories()).extracting(MonthlyAssessmentService.CategoryExpense::name)
                .containsExactly("食費", "住居費");
        assertThat(result.monthlyTrend()).hasSize(6);
        assertThat(result.monthlyTrend()).filteredOn(trend -> trend.month().equals("2026-06"))
                .singleElement().satisfies(trend -> {
                    assertThat(trend.expense()).isEqualByComparingTo("20000");
                    assertThat(trend.transactionCount()).isEqualTo(1);
                });
        assertThat(result.reviewComment()).contains("【良好】").contains("参考収支率は43.3%")
                .contains("後半3か月の支出は前半より増加").contains("食費");
        assertThat(result.activeGoals()).singleElement()
                .extracting(MonthlyAssessmentService.GoalProgress::progressPercent)
                .isEqualTo(new BigDecimal("50.0"));
        assertThat(result.findings()).anyMatch(finding -> finding.type().equals("cashflow_positive"));
        assertThat(result.dataQuality()).isEmpty();
    }

    @Test
    void doesNotInventSavingsRateOrNetWorthWhenInputsAreMissingOrDatesDiffer() {
        YearMonth month = YearMonth.of(2026, 10);
        when(transactionRepository.findByUserIdBetweenDates(7L, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 10, 31)))
                .thenReturn(List.of());
        when(assetSnapshotService.findRecentByUserId(7L)).thenReturn(List.of(
                new AssetSnapshotService.AssetSnapshot(LocalDate.of(2026, 10, 31), new BigDecimal("900000"),
                        null, null, null, null)));
        when(planningDataService.findLiabilities(7L)).thenReturn(List.of(
                new PlanningDataService.LongTermLiability(1L, "奨学金", "student_loan", new BigDecimal("200000"),
                        LocalDate.of(2026, 9, 30), null, null, null, null, LocalDateTime.now(), LocalDateTime.now())));
        when(planningDataService.findGoals(7L)).thenReturn(List.of());

        var result = service.evaluate(7L, month);

        assertThat(result.figures().income()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.figures().balanceRatePercent()).isNull();
        assertThat(result.liabilities().netWorth()).isNull();
        assertThat(result.reviewComment()).contains("家計の健全性を評価できません");
        assertThat(result.externalDataSent()).isFalse();
        assertThat(result.dataQuality()).hasSize(4);
        assertThat(result.findings()).anyMatch(finding -> finding.type().equals("data_missing"));
    }

    @Test
    void identifiesDeficitAndLowSavingsRateAsHouseholdHealthWarnings() {
        YearMonth month = YearMonth.of(2026, 10);
        LocalDate startDate = LocalDate.of(2026, 5, 1);
        LocalDate endDate = LocalDate.of(2026, 10, 31);
        List<Map<String, Object>> deficitTransactions = List.of(
                Map.of("type", "income", "amount", new BigDecimal("1000"),
                        "transaction_date", java.sql.Date.valueOf("2026-10-01")),
                Map.of("type", "expense", "amount", new BigDecimal("-1200"),
                        "transaction_date", java.sql.Date.valueOf("2026-10-02")));
        List<Map<String, Object>> lowSavingsTransactions = List.of(
                Map.of("type", "income", "amount", new BigDecimal("1000"),
                        "transaction_date", java.sql.Date.valueOf("2026-10-01")),
                Map.of("type", "expense", "amount", new BigDecimal("-950"),
                        "transaction_date", java.sql.Date.valueOf("2026-10-02")));
        when(transactionRepository.findByUserIdBetweenDates(7L, startDate, endDate))
                .thenReturn(deficitTransactions, lowSavingsTransactions);
        when(assetSnapshotService.findRecentByUserId(7L)).thenReturn(List.of());
        when(planningDataService.findLiabilities(7L)).thenReturn(List.of());
        when(planningDataService.findGoals(7L)).thenReturn(List.of());

        var deficit = service.evaluate(7L, month);
        var lowSavings = service.evaluate(7L, month);

        assertThat(deficit.reviewComment()).contains("【要確認】").contains("赤字");
        assertThat(lowSavings.reviewComment()).contains("【注意】").contains("5.0%").contains("10%を目安");
    }
}