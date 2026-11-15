package com.catsflow.catsflow.Service;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlanningDataService {

    private final JdbcTemplate jdbcTemplate;

    public PlanningDataService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<FinancialProfile> findProfile(Long userId) {
        List<FinancialProfile> profiles = jdbcTemplate.query(
                "SELECT user_id, housing_type, monthly_living_cost, income_stability, "
                        + "monthly_family_support, priorities, updated_at FROM financial_profiles WHERE user_id = ?",
                PROFILE_MAPPER,
                userId);
        return profiles.stream().findFirst();
    }

    @Transactional
    public FinancialProfile saveProfile(Long userId, ProfileInput input) {
        validateOptionalOneOf(input.housingType(), "住居形態", "living_with_family", "renting", "owned", "other");
        validateOptionalOneOf(input.incomeStability(), "収入の安定性", "stable", "variable", "unstable", "unknown");
        validateText(input.housingType(), 30, "住居形態");
        validateText(input.incomeStability(), 30, "収入の安定性");
        validateText(input.priorities(), 1000, "優先事項");
        validateNonNegative(input.monthlyLivingCost(), "月間生活費");
        validateNonNegative(input.monthlyFamilySupport(), "家族への支援額");

        int updated = jdbcTemplate.update(
                "UPDATE financial_profiles SET housing_type = ?, monthly_living_cost = ?, income_stability = ?, "
                        + "monthly_family_support = ?, priorities = ?, updated_at = CURRENT_TIMESTAMP WHERE user_id = ?",
                emptyToNull(input.housingType()), input.monthlyLivingCost(), emptyToNull(input.incomeStability()),
                input.monthlyFamilySupport(), emptyToNull(input.priorities()), userId);
        if (updated == 0) {
            jdbcTemplate.update(
                    "INSERT INTO financial_profiles (user_id, housing_type, monthly_living_cost, income_stability, "
                            + "monthly_family_support, priorities) VALUES (?, ?, ?, ?, ?, ?)",
                    userId, emptyToNull(input.housingType()), input.monthlyLivingCost(),
                    emptyToNull(input.incomeStability()), input.monthlyFamilySupport(),
                    emptyToNull(input.priorities()));
        }
        return findProfile(userId).orElseThrow();
    }

    public List<LongTermLiability> findLiabilities(Long userId) {
        return jdbcTemplate.query(
                "SELECT id, name, liability_type, balance, as_of_date, annual_interest_rate, monthly_payment, "
                        + "payment_day, note, created_at, updated_at FROM long_term_liabilities "
                        + "WHERE user_id = ? ORDER BY as_of_date DESC, id DESC",
                LIABILITY_MAPPER,
                userId);
    }

    public LongTermLiability createLiability(Long userId, LiabilityInput input) {
        validateLiability(input);
        Long id = insertAndReturnId(
                "INSERT INTO long_term_liabilities (user_id, name, liability_type, balance, as_of_date, "
                        + "annual_interest_rate, monthly_payment, payment_day, note) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                userId, input.name().trim(), input.type(), input.balance(), Date.valueOf(input.asOfDate()),
                input.annualInterestRate(), input.monthlyPayment(), input.paymentDay(), emptyToNull(input.note()));
        return findLiability(userId, id).orElseThrow();
    }

    public Optional<LongTermLiability> updateLiability(Long userId, Long id, LiabilityInput input) {
        validateLiability(input);
        int updated = jdbcTemplate.update(
                "UPDATE long_term_liabilities SET name = ?, liability_type = ?, balance = ?, as_of_date = ?, "
                        + "annual_interest_rate = ?, monthly_payment = ?, payment_day = ?, note = ?, "
                        + "updated_at = CURRENT_TIMESTAMP WHERE id = ? AND user_id = ?",
                input.name().trim(), input.type(), input.balance(), Date.valueOf(input.asOfDate()),
                input.annualInterestRate(), input.monthlyPayment(), input.paymentDay(), emptyToNull(input.note()),
                id, userId);
        return updated == 0 ? Optional.empty() : findLiability(userId, id);
    }

    public boolean deleteLiability(Long userId, Long id) {
        return jdbcTemplate.update("DELETE FROM long_term_liabilities WHERE id = ? AND user_id = ?", id, userId) > 0;
    }

    public List<FinancialGoal> findGoals(Long userId) {
        return jdbcTemplate.query(
                "SELECT id, goal_type, name, target_amount, current_amount, target_month, monthly_contribution, "
                        + "status, created_at, updated_at FROM financial_goals WHERE user_id = ? "
                        + "ORDER BY CASE status WHEN 'active' THEN 0 ELSE 1 END, target_month, id",
                GOAL_MAPPER,
                userId);
    }

    public FinancialGoal createGoal(Long userId, GoalInput input) {
        validateGoal(input);
        Long id = insertAndReturnId(
                "INSERT INTO financial_goals (user_id, goal_type, name, target_amount, current_amount, "
                        + "target_month, monthly_contribution, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                userId, input.type(), input.name().trim(), input.targetAmount(), input.currentAmount(),
                emptyToNull(input.targetMonth()), input.monthlyContribution(), input.status());
        return findGoal(userId, id).orElseThrow();
    }

    public Optional<FinancialGoal> updateGoal(Long userId, Long id, GoalInput input) {
        validateGoal(input);
        int updated = jdbcTemplate.update(
                "UPDATE financial_goals SET goal_type = ?, name = ?, target_amount = ?, current_amount = ?, "
                        + "target_month = ?, monthly_contribution = ?, status = ?, updated_at = CURRENT_TIMESTAMP "
                        + "WHERE id = ? AND user_id = ?",
                input.type(), input.name().trim(), input.targetAmount(), input.currentAmount(),
                emptyToNull(input.targetMonth()), input.monthlyContribution(), input.status(), id, userId);
        return updated == 0 ? Optional.empty() : findGoal(userId, id);
    }

    public boolean deleteGoal(Long userId, Long id) {
        return jdbcTemplate.update("DELETE FROM financial_goals WHERE id = ? AND user_id = ?", id, userId) > 0;
    }

    public List<LifePlanEvent> findLifePlanEvents(Long userId) {
        return jdbcTemplate.query(
                "SELECT id, title, category, event_month, estimated_amount, entry_type, note, created_at, updated_at "
                        + "FROM life_plan_events WHERE user_id = ? ORDER BY event_month, id",
                LIFE_EVENT_MAPPER,
                userId);
    }

    public LifePlanEvent createLifePlanEvent(Long userId, LifeEventInput input) {
        validateLifeEvent(input);
        Long id = insertAndReturnId(
                "INSERT INTO life_plan_events (user_id, title, category, event_month, estimated_amount, entry_type, note) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                userId, input.title().trim(), input.category(), input.eventMonth(), input.estimatedAmount(),
                input.entryType(), emptyToNull(input.note()));
        return findLifePlanEvent(userId, id).orElseThrow();
    }

    public Optional<LifePlanEvent> updateLifePlanEvent(Long userId, Long id, LifeEventInput input) {
        validateLifeEvent(input);
        int updated = jdbcTemplate.update(
                "UPDATE life_plan_events SET title = ?, category = ?, event_month = ?, estimated_amount = ?, "
                        + "entry_type = ?, note = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND user_id = ?",
                input.title().trim(), input.category(), input.eventMonth(), input.estimatedAmount(),
                input.entryType(), emptyToNull(input.note()), id, userId);
        return updated == 0 ? Optional.empty() : findLifePlanEvent(userId, id);
    }

    public boolean deleteLifePlanEvent(Long userId, Long id) {
        return jdbcTemplate.update("DELETE FROM life_plan_events WHERE id = ? AND user_id = ?", id, userId) > 0;
    }

    private Optional<LongTermLiability> findLiability(Long userId, Long id) {
        return jdbcTemplate.query(
                "SELECT id, name, liability_type, balance, as_of_date, annual_interest_rate, monthly_payment, "
                        + "payment_day, note, created_at, updated_at FROM long_term_liabilities WHERE user_id = ? AND id = ?",
                LIABILITY_MAPPER, userId, id).stream().findFirst();
    }

    private Optional<FinancialGoal> findGoal(Long userId, Long id) {
        return jdbcTemplate.query(
                "SELECT id, goal_type, name, target_amount, current_amount, target_month, monthly_contribution, "
                        + "status, created_at, updated_at FROM financial_goals WHERE user_id = ? AND id = ?",
                GOAL_MAPPER, userId, id).stream().findFirst();
    }

    private Optional<LifePlanEvent> findLifePlanEvent(Long userId, Long id) {
        return jdbcTemplate.query(
                "SELECT id, title, category, event_month, estimated_amount, entry_type, note, created_at, updated_at "
                        + "FROM life_plan_events WHERE user_id = ? AND id = ?",
                LIFE_EVENT_MAPPER, userId, id).stream().findFirst();
    }

    private Long insertAndReturnId(String sql, Object... parameters) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(sql, new String[] { "id" });
            for (int index = 0; index < parameters.length; index++) {
                statement.setObject(index + 1, parameters[index]);
            }
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null)
            throw new IllegalStateException("保存したレコードのIDを取得できませんでした。");
        return key.longValue();
    }

    private void validateLiability(LiabilityInput input) {
        validateRequiredText(input.name(), 120, "負債名");
        requireOneOf(input.type(), "負債種別", "student_loan", "mortgage", "car_loan", "other");
        requireNonNegative(input.balance(), "残高");
        if (input.asOfDate() == null)
            throw new IllegalArgumentException("基準日は必須です。");
        validateNonNegative(input.annualInterestRate(), "年利");
        validateNonNegative(input.monthlyPayment(), "月々の返済額");
        if (input.paymentDay() != null && (input.paymentDay() < 1 || input.paymentDay() > 31)) {
            throw new IllegalArgumentException("返済日は1〜31で入力してください。");
        }
        validateText(input.note(), 1000, "メモ");
    }

    private void validateGoal(GoalInput input) {
        requireOneOf(input.type(), "目標種別", "savings", "asset_building");
        validateRequiredText(input.name(), 120, "目標名");
        requirePositive(input.targetAmount(), "目標金額");
        requireNonNegative(input.currentAmount(), "現在額");
        validateMonth(input.targetMonth());
        validateNonNegative(input.monthlyContribution(), "月々の積立予定額");
        requireOneOf(input.status(), "状態", "active", "paused", "completed");
    }

    private void validateLifeEvent(LifeEventInput input) {
        validateRequiredText(input.title(), 120, "イベント名");
        requireOneOf(input.category(), "カテゴリ", "housing", "vehicle", "education", "family_support", "career",
                "large_purchase", "other");
        validateMonth(input.eventMonth());
        validateNonNegative(input.estimatedAmount(), "金額");
        requireOneOf(input.entryType(), "区分", "fact", "assumption");
        validateText(input.note(), 1000, "メモ");
    }

    private void validateMonth(String value) {
        if (value == null || !value.matches("^\\d{4}-(0[1-9]|1[0-2])$")) {
            throw new IllegalArgumentException("年月はYYYY-MM形式で入力してください。");
        }
    }

    private void requireOneOf(String value, String label, String... allowed) {
        if (value == null || java.util.Arrays.stream(allowed).noneMatch(value::equals)) {
            throw new IllegalArgumentException(label + "を選択してください。");
        }
    }

    private void validateOptionalOneOf(String value, String label, String... allowed) {
        if (value != null && !value.isBlank())
            requireOneOf(value, label, allowed);
    }

    private void validateText(String value, int maxLength, String label) {
        if (value != null && value.length() > maxLength) {
            throw new IllegalArgumentException(label + "は" + maxLength + "文字以内で入力してください。");
        }
    }

    private void validateRequiredText(String value, int maxLength, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + "を入力してください。");
        }
        validateText(value, maxLength, label);
    }

    private void requirePositive(BigDecimal value, String label) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(label + "は0より大きい値を入力してください。");
        }
    }

    private void requireNonNegative(BigDecimal value, String label) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(label + "は0以上の値を入力してください。");
        }
    }

    private void validateNonNegative(BigDecimal value, String label) {
        if (value != null && value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(label + "は0以上で入力してください。");
        }
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static final RowMapper<FinancialProfile> PROFILE_MAPPER = (rs, row) -> new FinancialProfile(
            rs.getLong("user_id"), rs.getString("housing_type"), rs.getBigDecimal("monthly_living_cost"),
            rs.getString("income_stability"), rs.getBigDecimal("monthly_family_support"),
            rs.getString("priorities"), toLocalDateTime(rs.getTimestamp("updated_at")));

    private static final RowMapper<LongTermLiability> LIABILITY_MAPPER = (rs, row) -> new LongTermLiability(
            rs.getLong("id"), rs.getString("name"), rs.getString("liability_type"), rs.getBigDecimal("balance"),
            rs.getDate("as_of_date").toLocalDate(), rs.getBigDecimal("annual_interest_rate"),
            rs.getBigDecimal("monthly_payment"), rs.getObject("payment_day", Integer.class), rs.getString("note"),
            toLocalDateTime(rs.getTimestamp("created_at")), toLocalDateTime(rs.getTimestamp("updated_at")));

    private static final RowMapper<FinancialGoal> GOAL_MAPPER = (rs, row) -> new FinancialGoal(
            rs.getLong("id"), rs.getString("goal_type"), rs.getString("name"), rs.getBigDecimal("target_amount"),
            rs.getBigDecimal("current_amount"), rs.getString("target_month"), rs.getBigDecimal("monthly_contribution"),
            rs.getString("status"), toLocalDateTime(rs.getTimestamp("created_at")),
            toLocalDateTime(rs.getTimestamp("updated_at")));

    private static final RowMapper<LifePlanEvent> LIFE_EVENT_MAPPER = (rs, row) -> new LifePlanEvent(
            rs.getLong("id"), rs.getString("title"), rs.getString("category"), rs.getString("event_month"),
            rs.getBigDecimal("estimated_amount"), rs.getString("entry_type"), rs.getString("note"),
            toLocalDateTime(rs.getTimestamp("created_at")), toLocalDateTime(rs.getTimestamp("updated_at")));

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    public record ProfileInput(String housingType, BigDecimal monthlyLivingCost, String incomeStability,
            BigDecimal monthlyFamilySupport, String priorities) {
    }

    public record LiabilityInput(String name, String type, BigDecimal balance, LocalDate asOfDate,
            BigDecimal annualInterestRate, BigDecimal monthlyPayment, Integer paymentDay, String note) {
    }

    public record GoalInput(String type, String name, BigDecimal targetAmount, BigDecimal currentAmount,
            String targetMonth, BigDecimal monthlyContribution, String status) {
    }

    public record LifeEventInput(String title, String category, String eventMonth, BigDecimal estimatedAmount,
            String entryType, String note) {
    }

    public record FinancialProfile(Long userId, String housingType, BigDecimal monthlyLivingCost,
            String incomeStability, BigDecimal monthlyFamilySupport, String priorities, LocalDateTime updatedAt) {
    }

    public record LongTermLiability(Long id, String name, String type, BigDecimal balance, LocalDate asOfDate,
            BigDecimal annualInterestRate, BigDecimal monthlyPayment, Integer paymentDay, String note,
            LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    public record FinancialGoal(Long id, String type, String name, BigDecimal targetAmount, BigDecimal currentAmount,
            String targetMonth, BigDecimal monthlyContribution, String status, LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    public record LifePlanEvent(Long id, String title, String category, String eventMonth,
            BigDecimal estimatedAmount, String entryType, String note, LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }
}