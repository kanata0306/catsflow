package com.catsflow.catsflow.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TransactionRepository {

    private final JdbcTemplate jdbcTemplate;

    public TransactionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> findByUserId(Long userId) {
        String query = "SELECT * FROM transactions WHERE user_id = ?";
        return jdbcTemplate.queryForList(query, userId);
    }

    public List<Map<String, Object>> findThisMonthByUserId(Long userId) {
        YearMonth currentMonth = YearMonth.now();
        LocalDate startDate = currentMonth.atDay(1);
        LocalDate endDate = currentMonth.atEndOfMonth();

        String query = "SELECT t.*, c.name AS category_name "
                + "FROM transactions t "
                + "LEFT JOIN categories c ON c.id = t.category_id "
                + "WHERE t.user_id = ? AND t.transaction_date >= ? AND t.transaction_date <= ?";

        return jdbcTemplate.queryForList(query, userId, Date.valueOf(startDate), Date.valueOf(endDate));
    }
}