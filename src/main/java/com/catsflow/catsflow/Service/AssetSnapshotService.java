package com.catsflow.catsflow.Service;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
public class AssetSnapshotService {

    private static final int SNAPSHOT_LIMIT = 60;

    private final JdbcTemplate jdbcTemplate;

    public AssetSnapshotService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AssetSnapshot> findRecentByUserId(Long userId) {
        return jdbcTemplate.query(
                "SELECT snapshot_date, total_amount, cash_amount, stock_amount, investment_trust_amount, point_amount "
                        + "FROM asset_snapshots WHERE user_id = ? ORDER BY snapshot_date DESC LIMIT ?",
                SNAPSHOT_ROW_MAPPER,
                userId,
                SNAPSHOT_LIMIT);
    }

    private static final RowMapper<AssetSnapshot> SNAPSHOT_ROW_MAPPER = (ResultSet rs, int rowNum) -> new AssetSnapshot(
            rs.getDate("snapshot_date").toLocalDate(),
            rs.getBigDecimal("total_amount"),
            rs.getBigDecimal("cash_amount"),
            rs.getBigDecimal("stock_amount"),
            rs.getBigDecimal("investment_trust_amount"),
            rs.getBigDecimal("point_amount"));

    public record AssetSnapshot(LocalDate snapshotDate, BigDecimal totalAmount, BigDecimal cashAmount,
            BigDecimal stockAmount, BigDecimal investmentTrustAmount, BigDecimal pointAmount) {
    }
}