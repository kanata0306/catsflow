package com.catsflow.catsflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.catsflow.catsflow.Service.BudgetService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class BudgetServiceTest {

    @Autowired
    private BudgetService budgetService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void returnsNullWhenBudgetNotSet() {
        var status = budgetService.getCurrentMonthBudgetStatus(1L);

        assertThat(status).isNull();
    }

    @Test
    void returnsBudgetStatusWithCalculation() {
        String yearMonth = YearMonth.now().toString();
        LocalDate start = YearMonth.now().atDay(1);

        // 予算を設定
        jdbcTemplate.update(
                "INSERT INTO budgets (user_id, category_id, year_month, amount) VALUES (?, ?, ?, ?)",
                1L, null, yearMonth, 100000);

        // 支出データを追加
        jdbcTemplate.update(
                "DELETE FROM transactions WHERE user_id = ? AND transaction_date >= ?",
                1L, start);
        jdbcTemplate.update(
                "INSERT INTO transactions (user_id, account_id, category_id, transaction_date, amount, type, description, memo) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                1L, 1L, 2L, start, -30000, "expense", "食費", "スーパー");

        var status = budgetService.getCurrentMonthBudgetStatus(1L);

        assertThat(status).isNotNull();
        assertThat(status.budgetAmount()).isEqualByComparingTo(new BigDecimal("100000.00"));
        assertThat(status.spentAmount()).isEqualByComparingTo(new BigDecimal("30000.00"));
        assertThat(status.remaining()).isEqualByComparingTo(new BigDecimal("70000.00"));
        assertThat(status.usageRate()).isEqualTo(30);
    }

    @Test
    void returnsCategoryBudgetStatuses() {
        String yearMonth = YearMonth.now().toString();
        LocalDate start = YearMonth.now().atDay(1);

        // カテゴリ別予算を設定
        jdbcTemplate.update(
                "INSERT INTO budgets (user_id, category_id, year_month, amount) VALUES (?, ?, ?, ?)",
                1L, 2L, yearMonth, 50000);

        // 支出データを追加
        jdbcTemplate.update(
                "DELETE FROM transactions WHERE user_id = ? AND transaction_date >= ?",
                1L, start);
        jdbcTemplate.update(
                "INSERT INTO transactions (user_id, account_id, category_id, transaction_date, amount, type, description, memo) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                1L, 1L, 2L, start, -20000, "expense", "食費", "スーパー");

        var statuses = budgetService.getCategoryBudgetStatuses(1L);

        assertThat(statuses).isNotEmpty();
        var foodBudget = statuses.stream()
                .filter(s -> "食費".equals(s.categoryName()))
                .findFirst();
        assertThat(foodBudget).isPresent();
        assertThat(foodBudget.get().budgetAmount()).isEqualByComparingTo(new BigDecimal("50000.00"));
        assertThat(foodBudget.get().spentAmount()).isEqualByComparingTo(new BigDecimal("20000.00"));
        assertThat(foodBudget.get().usageRate()).isEqualTo(40);
    }

    @Test
    void setBudgetCreatesNewRecord() {
        String yearMonth = "2026-11";

        budgetService.setBudget(1L, null, yearMonth, new BigDecimal("120000"));

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM budgets WHERE user_id = ? AND category_id IS NULL AND year_month = ?",
                Integer.class, 1L, yearMonth);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void setBudgetUpdatesExistingRecord() {
        String yearMonth = "2026-11";

        budgetService.setBudget(1L, null, yearMonth, new BigDecimal("120000"));
        budgetService.setBudget(1L, null, yearMonth, new BigDecimal("150000"));

        BigDecimal amount = jdbcTemplate.queryForObject(
                "SELECT amount FROM budgets WHERE user_id = ? AND category_id IS NULL AND year_month = ?",
                BigDecimal.class, 1L, yearMonth);
        assertThat(amount).isEqualByComparingTo(new BigDecimal("150000.00"));
    }
}
