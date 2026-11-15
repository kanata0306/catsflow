package com.catsflow.catsflow.Service;

import com.catsflow.catsflow.Repository.TransactionRepository;
import com.catsflow.catsflow.Service.AssetSnapshotService.AssetSnapshot;
import com.catsflow.catsflow.Service.PlanningDataService.FinancialGoal;
import com.catsflow.catsflow.Service.PlanningDataService.LongTermLiability;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class MonthlyAssessmentService {

    private final TransactionRepository transactionRepository;
    private final AssetSnapshotService assetSnapshotService;
    private final PlanningDataService planningDataService;

    public MonthlyAssessmentService(TransactionRepository transactionRepository,
            AssetSnapshotService assetSnapshotService,
            PlanningDataService planningDataService) {
        this.transactionRepository = transactionRepository;
        this.assetSnapshotService = assetSnapshotService;
        this.planningDataService = planningDataService;
    }

    public MonthlyAssessment evaluate(Long userId, YearMonth month) {
        int reviewWindowMonths = 6;
        YearMonth startMonth = month.minusMonths(reviewWindowMonths - 1L);
        LocalDate startDate = startMonth.atDay(1);
        LocalDate endDate = month.atEndOfMonth();
        List<Map<String, Object>> transactions = transactionRepository.findByUserIdBetweenDates(userId,
                startDate, endDate);
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        Map<String, BigDecimal> expensesByCategory = new HashMap<>();
        Map<YearMonth, MonthBucket> monthBuckets = new HashMap<>();
        for (int offset = 0; offset < reviewWindowMonths; offset++) {
            monthBuckets.put(startMonth.plusMonths(offset), new MonthBucket());
        }

        for (Map<String, Object> transaction : transactions) {
            String type = value(transaction.get("type"));
            BigDecimal transactionAmount = decimal(transaction.get("amount")).abs();
            YearMonth transactionMonth = transactionMonth(transaction.get("transaction_date"));
            MonthBucket monthBucket = monthBuckets.get(transactionMonth);
            if (monthBucket == null) {
                continue;
            }
            monthBucket.transactionCount++;
            if ("income".equalsIgnoreCase(type)) {
                income = income.add(transactionAmount);
                monthBucket.income = monthBucket.income.add(transactionAmount);
            } else if ("expense".equalsIgnoreCase(type)) {
                expense = expense.add(transactionAmount);
                monthBucket.expense = monthBucket.expense.add(transactionAmount);
                String category = value(transaction.get("category_name"));
                expensesByCategory.merge(category.isBlank() ? "未分類" : category, transactionAmount, BigDecimal::add);
                monthBucket.expensesByCategory.merge(category.isBlank() ? "未分類" : category,
                        transactionAmount, BigDecimal::add);
            }
        }

        BigDecimal balance = income.subtract(expense);
        BigDecimal balanceRate = income.signum() == 0
                ? null
                : balance.multiply(BigDecimal.valueOf(100)).divide(income, 1, RoundingMode.HALF_UP);
        List<CategoryExpense> categories = expensesByCategory.entrySet().stream()
                .map(entry -> new CategoryExpense(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(CategoryExpense::amount).reversed())
                .limit(5)
                .toList();
        List<MonthlyTrend> monthlyTrend = monthBuckets.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new MonthlyTrend(entry.getKey().toString(), entry.getValue().income,
                        entry.getValue().expense, entry.getValue().income.subtract(entry.getValue().expense),
                        entry.getValue().transactionCount))
                .toList();

        List<AssetSnapshot> snapshots = assetSnapshotService.findRecentByUserId(userId).stream()
                .filter(snapshot -> !snapshot.snapshotDate().isBefore(startDate)
                        && !snapshot.snapshotDate().isAfter(endDate))
                .toList();
        AssetSnapshot latestAsset = snapshots.isEmpty() ? null : snapshots.get(0);
        List<LongTermLiability> allLiabilities = planningDataService.findLiabilities(userId);
        List<LongTermLiability> liabilities = allLiabilities.stream()
                .filter(liability -> !liability.asOfDate().isBefore(startDate)
                        && !liability.asOfDate().isAfter(endDate))
                .toList();
        LiabilitySummary liabilitySummary = summarizeLiabilities(latestAsset, liabilities);

        List<FinancialGoal> activeGoals = planningDataService.findGoals(userId).stream()
                .filter(goal -> "active".equals(goal.status()))
                .toList();
        List<GoalProgress> goalProgresses = activeGoals.stream()
                .map(goal -> toGoalProgress(goal))
                .toList();

        List<String> dataQuality = new ArrayList<>();
        if (transactions.isEmpty()) {
            dataQuality.add("直近6か月に計算対象取引がありません。");
        }
        if (latestAsset == null) {
            dataQuality.add("直近6か月の資産スナップショットがありません。");
        }
        if (liabilities.isEmpty()) {
            dataQuality.add(allLiabilities.isEmpty()
                    ? "長期負債が未登録のため、純資産は算出していません。"
                    : "直近6か月に長期負債の残高記録がありません。");
        } else if (liabilitySummary.asOfDate() == null) {
            dataQuality.add("長期負債同士の基準日が一致しないため、純資産は算出していません。");
        } else if (latestAsset == null || !latestAsset.snapshotDate().equals(liabilitySummary.asOfDate())) {
            dataQuality.add("資産と負債の基準日が一致しないため、純資産は算出していません。");
        }
        if (goalProgresses.isEmpty()) {
            dataQuality.add("有効な目標がありません。");
        }
        if (income.signum() == 0) {
            dataQuality.add("直近6か月の収入が0件のため、収支率は算出していません。");
        }

        List<ReviewFinding> findings = buildFindings(transactions.size(), income, expense,
                balance, categories, latestAsset, liabilitySummary, goalProgresses);
        MonthlyFigures figures = new MonthlyFigures(income, expense, balance, balanceRate, transactions.size());
        String reviewComment = buildReviewComment(transactions.size(), income, expense, balance, balanceRate,
                categories, monthlyTrend);

        return new MonthlyAssessment(startMonth.toString(), month.toString(), reviewWindowMonths,
                "rule_based", "not_configured", false, reviewComment, figures, monthlyTrend,
                latestAsset == null ? null : toAssetSummary(latestAsset), liabilitySummary,
                goalProgresses, categories, findings, dataQuality);
    }

    private LiabilitySummary summarizeLiabilities(AssetSnapshot latestAsset, List<LongTermLiability> liabilities) {
        if (liabilities.isEmpty()) {
            return new LiabilitySummary(0, null, null, null);
        }

        LocalDate liabilityDate = liabilities.get(0).asOfDate();
        boolean sameLiabilityDate = liabilities.stream().allMatch(item -> liabilityDate.equals(item.asOfDate()));
        if (!sameLiabilityDate) {
            return new LiabilitySummary(liabilities.size(), null, null, null);
        }

        BigDecimal totalBalance = liabilities.stream()
                .map(LongTermLiability::balance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal netWorth = latestAsset != null && latestAsset.snapshotDate().equals(liabilityDate)
                ? latestAsset.totalAmount().subtract(totalBalance)
                : null;
        return new LiabilitySummary(liabilities.size(), liabilityDate, totalBalance, netWorth);
    }

    private List<ReviewFinding> buildFindings(int transactionCount, BigDecimal income, BigDecimal expense,
            BigDecimal balance, List<CategoryExpense> categories, AssetSnapshot latestAsset,
            LiabilitySummary liabilities, List<GoalProgress> goals) {
        List<ReviewFinding> findings = new ArrayList<>();
        if (transactionCount == 0) {
            findings.add(new ReviewFinding("data_missing", "取引データがありません",
                    "直近6か月に計算対象取引がないため、収支についての評価はできません。", List.of()));
        } else {
            findings.add(new ReviewFinding(
                    balance.signum() >= 0 ? "cashflow_positive" : "cashflow_negative",
                    balance.signum() >= 0 ? "直近6か月の収入が支出を上回っています" : "直近6か月の支出が収入を上回っています",
                    "直近6か月の計算対象取引を集計しています。これは運用成績や資産増加を示すものではありません。",
                    List.of(new Evidence("収入", income), new Evidence("支出", expense), new Evidence("収支", balance))));
            if (!categories.isEmpty()) {
                CategoryExpense topCategory = categories.get(0);
                findings.add(new ReviewFinding("category_fact", "6か月間で支出額が最も大きいカテゴリ",
                        topCategory.name() + "が最大です。", List.of(new Evidence("支出額", topCategory.amount()))));
            }
        }

        if (latestAsset != null) {
            findings.add(new ReviewFinding("asset_fact", "最新の資産記録",
                    "保存されている最新スナップショットの基準日と金額です。",
                    List.of(new Evidence("資産総額", latestAsset.totalAmount()))));
        }
        if (liabilities.netWorth() != null) {
            findings.add(new ReviewFinding("net_worth_fact", "同一基準日の純資産",
                    "資産と長期負債の基準日が一致する場合のみ算出しています。",
                    List.of(new Evidence("長期負債", liabilities.totalBalance()),
                            new Evidence("純資産", liabilities.netWorth()))));
        }
        if (!goals.isEmpty()) {
            findings.add(new ReviewFinding("goal_fact", "有効な目標",
                    "登録済みの有効目標を表示しています。達成予測は含みません。",
                    List.of(new Evidence("目標数", BigDecimal.valueOf(goals.size())))));
        }
        return List.copyOf(findings);
    }

    private String buildReviewComment(int transactionCount, BigDecimal income, BigDecimal expense,
            BigDecimal balance, BigDecimal balanceRate, List<CategoryExpense> categories,
            List<MonthlyTrend> monthlyTrend) {
        if (transactionCount == 0) {
            return "直近6か月の取引記録がないため、家計の健全性を評価できません。取込データの期間を確認してください。";
        }
        if (income.signum() == 0 || balanceRate == null) {
            return "直近6か月の収入記録がないため、家計の健全性を評価できません。収入データを確認してください。";
        }
        String health;
        if (balance.signum() < 0) {
            health = "【要確認】直近6か月は支出が収入を上回り、家計収支が赤字です。";
        } else if (balanceRate.compareTo(BigDecimal.TEN) < 0) {
            health = "【注意】直近6か月は黒字ですが、参考収支率は" + balanceRate.toPlainString()
                    + "%です。10%を目安に、支出とのバランスを確認してください。";
        } else {
            health = "【良好】直近6か月は黒字で、参考収支率は" + balanceRate.toPlainString()
                    + "%です。収支の健全性が保たれています。";
        }
        String category = categories.isEmpty()
                ? ""
                : "支出が最も多い分類は「" + categories.get(0).name() + "」です。";
        BigDecimal firstHalfExpense = monthlyTrend.stream().limit(3)
                .map(MonthlyTrend::expense).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal secondHalfExpense = monthlyTrend.stream().skip(3)
                .map(MonthlyTrend::expense).reduce(BigDecimal.ZERO, BigDecimal::add);
        String expenseTrend = secondHalfExpense.compareTo(firstHalfExpense) < 0
                ? "後半3か月の支出は前半より減少しています。"
                : secondHalfExpense.compareTo(firstHalfExpense) > 0
                        ? "後半3か月の支出は前半より増加しています。"
                        : "前後半の支出額は同程度です。";
        return health + expenseTrend + category;
    }

    private YearMonth transactionMonth(Object value) {
        if (value instanceof java.sql.Date date) {
            return YearMonth.from(date.toLocalDate());
        }
        if (value instanceof LocalDate date) {
            return YearMonth.from(date);
        }
        return YearMonth.from(LocalDate.parse(String.valueOf(value)));
    }

    private static class MonthBucket {
        private BigDecimal income = BigDecimal.ZERO;
        private BigDecimal expense = BigDecimal.ZERO;
        private int transactionCount;
        private final Map<String, BigDecimal> expensesByCategory = new HashMap<>();
    }

    private AssetSnapshotSummary toAssetSummary(AssetSnapshot snapshot) {
        return new AssetSnapshotSummary(snapshot.snapshotDate(), snapshot.totalAmount(), snapshot.cashAmount(),
                snapshot.stockAmount(), snapshot.investmentTrustAmount(), snapshot.pointAmount());
    }

    private GoalProgress toGoalProgress(FinancialGoal goal) {
        BigDecimal percent = goal.targetAmount().signum() == 0
                ? null
                : goal.currentAmount().multiply(BigDecimal.valueOf(100))
                        .divide(goal.targetAmount(), 1, RoundingMode.HALF_UP);
        return new GoalProgress(goal.id(), goal.name(), goal.type(), goal.currentAmount(), goal.targetAmount(),
                percent);
    }

    private static BigDecimal decimal(Object value) {
        if (value == null)
            return BigDecimal.ZERO;
        if (value instanceof BigDecimal amount)
            return amount;
        if (value instanceof Number number)
            return BigDecimal.valueOf(number.doubleValue());
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return BigDecimal.ZERO;
        }
    }

    private static String value(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    public record MonthlyAssessment(String startMonth, String endMonth, int periodMonths, String assessmentMode,
            String aiStatus, boolean externalDataSent, String reviewComment, MonthlyFigures figures,
            List<MonthlyTrend> monthlyTrend, AssetSnapshotSummary latestAsset, LiabilitySummary liabilities,
            List<GoalProgress> activeGoals, List<CategoryExpense> topCategories, List<ReviewFinding> findings,
            List<String> dataQuality) {
    }

    public record MonthlyTrend(String month, BigDecimal income, BigDecimal expense, BigDecimal balance,
            int transactionCount) {
    }

    public record MonthlyFigures(BigDecimal income, BigDecimal expense, BigDecimal balance,
            BigDecimal balanceRatePercent, int transactionCount) {
    }

    public record AssetSnapshotSummary(LocalDate snapshotDate, BigDecimal totalAmount, BigDecimal cashAmount,
            BigDecimal stockAmount, BigDecimal investmentTrustAmount, BigDecimal pointAmount) {
    }

    public record LiabilitySummary(int count, LocalDate asOfDate, BigDecimal totalBalance, BigDecimal netWorth) {
    }

    public record GoalProgress(Long id, String name, String type, BigDecimal currentAmount,
            BigDecimal targetAmount, BigDecimal progressPercent) {
    }

    public record CategoryExpense(String name, BigDecimal amount) {
    }

    public record Evidence(String label, BigDecimal value) {
    }

    public record ReviewFinding(String type, String title, String detail, List<Evidence> evidence) {
    }
}