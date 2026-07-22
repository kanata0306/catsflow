package com.catsflow.catsflow.hello.Repository;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class HelloRepository {

    private final JdbcTemplate jdbcTemplate;

    public HelloRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> findByUserId(Long userId) {
        String query = "SELECT * FROM transactions WHERE user_id = ?";
        return jdbcTemplate.queryForList(query, userId);
    }
}