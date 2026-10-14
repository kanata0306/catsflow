package com.catsflow.catsflow.Repository;

import com.catsflow.catsflow.Entity.LineUserLink;
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
public class LineUserLinkRepository {

    private final JdbcTemplate jdbcTemplate;

    public LineUserLinkRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public LineUserLink save(LineUserLink link) {
        String sql = "INSERT INTO line_user_links (user_id, line_user_id, status, linked_at, updated_at) "
                + "VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[] { "id" });
            ps.setLong(1, link.getUserId());
            ps.setString(2, link.getLineUserId());
            ps.setString(3, link.getStatus());
            return ps;
        }, keyHolder);

        if (keyHolder.getKey() != null) {
            link.setId(keyHolder.getKey().longValue());
        }
        return link;
    }

    public Optional<LineUserLink> findByLineUserId(String lineUserId) {
        String sql = "SELECT * FROM line_user_links WHERE line_user_id = ?";
        try {
            LineUserLink link = jdbcTemplate.queryForObject(sql, lineUserLinkRowMapper(), lineUserId);
            return Optional.ofNullable(link);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public void deleteByLineUserId(String lineUserId) {
        String sql = "DELETE FROM line_user_links WHERE line_user_id = ?";
        jdbcTemplate.update(sql, lineUserId);
    }

    private RowMapper<LineUserLink> lineUserLinkRowMapper() {
        return (rs, rowNum) -> {
            LineUserLink link = new LineUserLink();
            link.setId(rs.getLong("id"));
            link.setUserId(rs.getLong("user_id"));
            link.setLineUserId(rs.getString("line_user_id"));
            link.setStatus(rs.getString("status"));

            Timestamp linkedAt = rs.getTimestamp("linked_at");
            if (linkedAt != null) {
                link.setLinkedAt(linkedAt.toLocalDateTime());
            }

            Timestamp updatedAt = rs.getTimestamp("updated_at");
            if (updatedAt != null) {
                link.setUpdatedAt(updatedAt.toLocalDateTime());
            }
            return link;
        };
    }
}
