package com.catsflow.catsflow.hello.Repository;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class HelloRepository {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    public List<Map<String, Object>> findByUserId(Long userId) {
        String query = "SELECT * FROM transactions WHERE user_id = ?";
        return jdbcTemplate.queryForList(query, userId);
    }
}
