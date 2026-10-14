package com.catsflow.catsflow.Repository;

import com.catsflow.catsflow.Entity.User;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<User> findByEmail(String email) {
        List<User> users = jdbcTemplate.query(
                "SELECT id, name, email, password_hash FROM users WHERE LOWER(email) = LOWER(?)",
                (rs, rowNum) -> {
                    User user = new User();
                    user.setId(rs.getLong("id"));
                    user.setName(rs.getString("name"));
                    user.setEmail(rs.getString("email"));
                    user.setPasswordHash(rs.getString("password_hash"));
                    return user;
                },
                email);
        return users.stream().findFirst();
    }

    public User create(String name, String email, String passwordHash) {
        String sql = "INSERT INTO users (name, email, password_hash) VALUES (?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, new String[] { "id" });
            statement.setString(1, name);
            statement.setString(2, email);
            statement.setString(3, passwordHash);
            return statement;
        }, keyHolder);

        User user = new User();
        user.setId(keyHolder.getKey().longValue());
        user.setName(name);
        user.setEmail(email);
        user.setPasswordHash(passwordHash);
        return user;
    }
}
