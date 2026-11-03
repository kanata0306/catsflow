package com.catsflow.catsflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.catsflow.catsflow.Repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Transactional
    void createsUserAndDefaultAccountAfterSeedData() {
        Long previousMaxUserId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM users", Long.class);
        Long previousMaxAccountId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM accounts", Long.class);

        var user = userRepository.create("New User", "new-user@example.com", "password-hash");
        int accountInsertCount = jdbcTemplate.update(
                "INSERT INTO accounts (user_id, name, type) VALUES (?, ?, ?)",
                user.getId(), "現金", "現金");

        assertThat(user.getId()).isGreaterThan(previousMaxUserId);
        assertThat(accountInsertCount).isEqualTo(1);
        Long newAccountId = jdbcTemplate.queryForObject(
                "SELECT id FROM accounts WHERE user_id = ? AND name = ?",
                Long.class, user.getId(), "現金");
        assertThat(newAccountId).isGreaterThan(previousMaxAccountId);
    }
}