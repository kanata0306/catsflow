package com.catsflow.catsflow.Service;

import com.catsflow.catsflow.Repository.TransactionRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class TransactionSummaryService {

    private final TransactionRepository transactionRepository;

    public TransactionSummaryService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    /**
     * 今月の収入・支出・収支を返す。
     * DB上の金額は支出が負数で保存されている前提で、支出は絶対値で集計する。
     */
    public MonthlySummary getMonthlySummary(Long userId) {
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;

        for (Map<String, Object> row : transactionRepository.findThisMonthByUserId(userId)) {
            BigDecimal amount = toDecimal(row.get("amount"));
            String type = String.valueOf(row.getOrDefault("type", "")).trim();

            if ("income".equalsIgnoreCase(type)) {
                income = income.add(amount.abs());
            } else if ("expense".equalsIgnoreCase(type)) {
                expense = expense.add(amount.abs());
            }
        }

        BigDecimal balance = income.subtract(expense);
        return new MonthlySummary(income, expense, balance);
    }

    /**
     * 今月のカテゴリ別支出を合計し、支出額の大きい順に返す。
     */
    public List<CategorySummary> getCategorySummary(Long userId) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();

        for (Map<String, Object> row : transactionRepository.findThisMonthByUserId(userId)) {
            String type = String.valueOf(row.getOrDefault("type", "")).trim();
            if (!"expense".equalsIgnoreCase(type)) {
                continue;
            }

            String categoryName = String.valueOf(row.getOrDefault("category_name", "未分類"));
            BigDecimal amount = toDecimal(row.get("amount")).abs();
            totals.merge(categoryName, amount, BigDecimal::add);
        }

        return totals.entrySet().stream()
                .map(entry -> new CategorySummary(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(CategorySummary::getTotal).reversed())
                .toList();
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

    public static class MonthlySummary {
        private final BigDecimal income;
        private final BigDecimal expense;
        private final BigDecimal balance;

        public MonthlySummary(BigDecimal income, BigDecimal expense, BigDecimal balance) {
            this.income = income;
            this.expense = expense;
            this.balance = balance;
        }

        public BigDecimal getIncome() {
            return income;
        }

        public BigDecimal getExpense() {
            return expense;
        }

        public BigDecimal getBalance() {
            return balance;
        }
    }

    public static class CategorySummary {
        private final String categoryName;
        private final BigDecimal total;

        public CategorySummary(String categoryName, BigDecimal total) {
            this.categoryName = categoryName;
            this.total = total;
        }

        public String getCategoryName() {
            return categoryName;
        }

        public BigDecimal getTotal() {
            return total;
        }
    }
}
