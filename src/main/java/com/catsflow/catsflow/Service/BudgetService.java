package com.catsflow.catsflow.Service;

import com.catsflow.catsflow.Repository.TransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class BudgetService {

    private final JdbcTemplate jdbcTemplate;
    private final TransactionRepository transactionRepository;

    public BudgetService(JdbcTemplate jdbcTemplate, TransactionRepository transactionRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionRepository = transactionRepository;
    }

    /**
     * 指定ユーザーの当月予算状況を返す。
     * 予算が未設定の場合はnullを返す。
     */
    public BudgetStatus getCurrentMonthBudgetStatus(Long userId) {
        String yearMonth = YearMonth.now().toString(); // yyyy-MM形式

        // 全体予算（category_id IS NULL）を取得
        BigDecimal budgetAmount = findBudgetAmount(userId, null, yearMonth);
        if (budgetAmount == null) {
            return null; // 予算未設定
        }

        // 当月の支出合計を取得（振替・計算対象外を除く）
        BigDecimal spentAmount = getCurrentMonthExpenseTotal(userId);

        BigDecimal remaining = budgetAmount.subtract(spentAmount);
        int usageRate = budgetAmount.compareTo(BigDecimal.ZERO) > 0
                ? spentAmount.multiply(BigDecimal.valueOf(100))
                        .divide(budgetAmount, 0, RoundingMode.HALF_UP)
                        .intValue()
                : 0;

        return new BudgetStatus(budgetAmount, spentAmount, remaining, usageRate);
    }

    /**
     * カテゴリ別の予算状況一覧を返す。
     */
    public List<CategoryBudgetStatus> getCategoryBudgetStatuses(Long userId) {
        String yearMonth = YearMonth.now().toString();

        // カテゴリ別予算を取得
        List<Map<String, Object>> budgets = jdbcTemplate.queryForList(
                "SELECT b.category_id, b.amount, c.name AS category_name "
                        + "FROM budgets b "
                        + "LEFT JOIN categories c ON c.id = b.category_id "
                        + "WHERE b.user_id = ? AND b.category_id IS NOT NULL AND b.year_month = ?",
                userId, yearMonth);

        // 当月のカテゴリ別支出を取得
        Map<String, BigDecimal> expenseByCategory = new java.util.HashMap<>();
        for (var row : transactionRepository.findThisMonthByUserId(userId)) {
            String type = String.valueOf(row.getOrDefault("type", "")).trim();
            if (!"expense".equalsIgnoreCase(type)) {
                continue;
            }
            String categoryName = String.valueOf(row.getOrDefault("category_name", "未分類"));
            BigDecimal amount = toDecimal(row.get("amount")).abs();
            expenseByCategory.merge(categoryName, amount, BigDecimal::add);
        }

        return budgets.stream()
                .map(row -> {
                    String categoryName = String.valueOf(row.get("category_name"));
                    BigDecimal budgetAmount = toDecimal(row.get("amount"));
                    BigDecimal spentAmount = expenseByCategory.getOrDefault(categoryName, BigDecimal.ZERO);
                    BigDecimal remaining = budgetAmount.subtract(spentAmount);
                    int usageRate = budgetAmount.compareTo(BigDecimal.ZERO) > 0
                            ? spentAmount.multiply(BigDecimal.valueOf(100))
                                    .divide(budgetAmount, 0, RoundingMode.HALF_UP)
                                    .intValue()
                            : 0;
                    return new CategoryBudgetStatus(categoryName, budgetAmount, spentAmount, remaining, usageRate);
                })
                .toList();
    }

    /**
     * 予算を設定または更新する。
     */
    public void setBudget(Long userId, Long categoryId, String yearMonth, BigDecimal amount) {
        int updated = jdbcTemplate.update(
                "UPDATE budgets SET amount = ?, updated_at = CURRENT_TIMESTAMP "
                        + "WHERE user_id = ? AND category_id IS NOT DISTINCT FROM ? AND year_month = ?",
                amount, userId, categoryId, yearMonth);

        if (updated == 0) {
            jdbcTemplate.update(
                    "INSERT INTO budgets (user_id, category_id, year_month, amount) VALUES (?, ?, ?, ?)",
                    userId, categoryId, yearMonth, amount);
        }
    }

    private BigDecimal findBudgetAmount(Long userId, Long categoryId, String yearMonth) {
        List<BigDecimal> results = jdbcTemplate.queryForList(
                "SELECT amount FROM budgets "
                        + "WHERE user_id = ? AND category_id IS NOT DISTINCT FROM ? AND year_month = ?",
                BigDecimal.class,
                userId, categoryId, yearMonth);
        return results.isEmpty() ? null : results.get(0);
    }

    private BigDecimal getCurrentMonthExpenseTotal(Long userId) {
        BigDecimal total = BigDecimal.ZERO;
        for (var row : transactionRepository.findThisMonthByUserId(userId)) {
            String type = String.valueOf(row.getOrDefault("type", "")).trim();
            if ("expense".equalsIgnoreCase(type)) {
                total = total.add(toDecimal(row.get("amount")).abs());
            }
        }
        return total;
    }

    private BigDecimal toDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    public record BudgetStatus(BigDecimal budgetAmount, BigDecimal spentAmount, BigDecimal remaining,
            int usageRate) {
    }

    public record CategoryBudgetStatus(String categoryName, BigDecimal budgetAmount, BigDecimal spentAmount,
            BigDecimal remaining, int usageRate) {
    }
}
