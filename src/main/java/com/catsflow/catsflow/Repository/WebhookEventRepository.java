package com.catsflow.catsflow.Repository;

import com.catsflow.catsflow.Entity.WebhookEvent;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class WebhookEventRepository {

    private final JdbcTemplate jdbcTemplate;

    public WebhookEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean existsByEventId(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM webhook_events WHERE event_id = ?",
                Integer.class,
                eventId);
        return count != null && count > 0;
    }

    public WebhookEvent save(WebhookEvent event) {
        if (event == null || event.getEventId() == null || event.getEventId().isBlank()) {
            return event;
        }

        String sql = "INSERT INTO webhook_events (event_id, user_id, event_type, payload_hash, processed_at) "
                + "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)";

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[] { "id" });
            ps.setString(1, event.getEventId());
            ps.setString(2, event.getUserId());
            ps.setString(3, event.getEventType());
            ps.setString(4, event.getPayloadHash());
            return ps;
        }, keyHolder);

        if (keyHolder.getKey() != null) {
            event.setId(keyHolder.getKey().longValue());
        }
        return event;
    }

    public Optional<WebhookEvent> findByEventId(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return Optional.empty();
        }
        try {
            WebhookEvent event = jdbcTemplate.queryForObject(
                    "SELECT * FROM webhook_events WHERE event_id = ?",
                    webhookEventRowMapper(),
                    eventId);
            return Optional.ofNullable(event);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private RowMapper<WebhookEvent> webhookEventRowMapper() {
        return (rs, rowNum) -> {
            WebhookEvent event = new WebhookEvent();
            event.setId(rs.getLong("id"));
            event.setEventId(rs.getString("event_id"));
            event.setUserId(rs.getString("user_id"));
            event.setEventType(rs.getString("event_type"));
            event.setPayloadHash(rs.getString("payload_hash"));
            Timestamp processedAt = rs.getTimestamp("processed_at");
            if (processedAt != null) {
                event.setProcessedAt(processedAt.toLocalDateTime());
            }
            return event;
        };
    }
}
