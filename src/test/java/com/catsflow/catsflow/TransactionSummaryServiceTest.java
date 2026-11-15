package com.catsflow.catsflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.catsflow.catsflow.Repository.TransactionRepository;
import com.catsflow.catsflow.Service.TransactionSummaryService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class TransactionSummaryServiceTest {

        @Autowired
        private TransactionSummaryService service;

        @Autowired
        private TransactionRepository transactionRepository;

        @Autowired
        private JdbcTemplate jdbcTemplate;

        @Test
        void monthlySummaryUsesCurrentMonthData() {
                LocalDate start = YearMonth.now().atDay(1);
                LocalDate end = YearMonth.now().atEndOfMonth();

                jdbcTemplate.update(
                                "DELETE FROM transactions WHERE user_id = ? AND transaction_date BETWEEN ? AND ?",
                                1L, start, end);
                jdbcTemplate.update(
                                "INSERT INTO transactions (user_id, account_id, category_id, transaction_date, amount, type, description, memo) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                                1L, 1L, 1L, start, 250000, "income", "今回の給与", "現在月の入金");
                jdbcTemplate.update(
                                "INSERT INTO transactions (user_id, account_id, category_id, transaction_date, amount, type, description, memo) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                                1L, 1L, 2L, start.plusDays(5), -3500, "expense", "食費", "スーパー");
                jdbcTemplate.update(
                                "INSERT INTO transactions (user_id, account_id, category_id, transaction_date, amount, type, description, memo) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                                1L, 1L, 3L, start.plusDays(10), -800, "expense", "交通費", "電車");

                var summary = service.getMonthlySummary(1L);

                assertThat(summary.getIncome()).isEqualByComparingTo(new BigDecimal("250000.00"));
                assertThat(summary.getExpense()).isEqualByComparingTo(new BigDecimal("4300.00"));
                assertThat(summary.getBalance()).isEqualByComparingTo(new BigDecimal("245700.00"));
        }

        @Test
        void categorySummaryReturnsOrderedExpenseTotals() {
                LocalDate start = YearMonth.now().atDay(1);
                LocalDate end = YearMonth.now().atEndOfMonth();

                jdbcTemplate.update(
                                "DELETE FROM transactions WHERE user_id = ? AND transaction_date BETWEEN ? AND ?",
                                1L, start, end);
                jdbcTemplate.update(
                                "INSERT INTO transactions (user_id, account_id, category_id, transaction_date, amount, type, description, memo) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                                1L, 1L, 2L, start.plusDays(2), -3500, "expense", "食費", "スーパー");
                jdbcTemplate.update(
                                "INSERT INTO transactions (user_id, account_id, category_id, transaction_date, amount, type, description, memo) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                                1L, 1L, 3L, start.plusDays(4), -800, "expense", "交通費", "電車");

                var categories = service.getCategorySummary(1L);

                assertThat(categories).isNotEmpty();
                assertThat(categories.get(0).getCategoryName()).isEqualTo("食費");
                assertThat(categories.get(0).getTotal()).isEqualByComparingTo(new BigDecimal("3500.00"));
        }

        @Test
        void loadsSelectedMonthAndExcludesTransferAndNonCalculationRows() {
                YearMonth month = YearMonth.of(2026, 10);
                LocalDate start = month.atDay(1);
                LocalDate end = month.atEndOfMonth();
                jdbcTemplate.update(
                                "DELETE FROM transactions WHERE user_id = ? AND transaction_date BETWEEN ? AND ?",
                                1L, start.minusDays(1), end.plusDays(1));
                jdbcTemplate.update(
                                "INSERT INTO transactions (user_id, account_id, category_id, transaction_date, amount, type, description, is_transfer, is_calculation_target) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                                1L, 1L, 2L, start, -1200, "expense", "対象", false, true);
                jdbcTemplate.update(
                                "INSERT INTO transactions (user_id, account_id, category_id, transaction_date, amount, type, description, is_transfer, is_calculation_target) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                                1L, 1L, 2L, start.plusDays(1), -3000, "expense", "振替", true, true);
                jdbcTemplate.update(
                                "INSERT INTO transactions (user_id, account_id, category_id, transaction_date, amount, type, description, is_transfer, is_calculation_target) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                                1L, 1L, 2L, end, -500, "expense", "対象外", false, false);
                jdbcTemplate.update(
                                "INSERT INTO transactions (user_id, account_id, category_id, transaction_date, amount, type, description, is_transfer, is_calculation_target) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                                1L, 1L, 2L, end.plusDays(1), -900, "expense", "翌月", false, true);

                var rows = transactionRepository.findByUserIdAndMonth(1L, month);

                assertThat(rows).hasSize(1);
                assertThat(rows.get(0)).containsEntry("DESCRIPTION", "対象");
        }
}
