package com.catsflow.catsflow.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@org.springframework.context.annotation.Profile("dev")
@ConditionalOnProperty(name = "catsflow.dev-seed.enabled", havingValue = "true")
public class DevPlanningSeedService {

    private final JdbcTemplate jdbcTemplate;

    public DevPlanningSeedService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void seedForNewUser(Long userId) {
        jdbcTemplate.update(
                "INSERT INTO financial_profiles (user_id, housing_type, monthly_living_cost, income_stability, "
                        + "monthly_family_support, priorities) VALUES (?, ?, ?, ?, ?, ?)",
                userId, "living_with_family", 45000, "stable", 20000,
                "開発用サンプル。入力内容は自由に編集できます。");
        jdbcTemplate.update(
                "INSERT INTO long_term_liabilities (user_id, name, liability_type, balance, as_of_date, "
                        + "annual_interest_rate, monthly_payment, payment_day, note) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                userId, "奨学金（サンプル）", "student_loan", 820000, LocalDate.now(), 0.65, 12000, 27,
                "開発用テストデータ。自由に編集・削除できます。");
        jdbcTemplate.update(
                "INSERT INTO financial_goals (user_id, goal_type, name, target_amount, current_amount, "
                        + "target_month, monthly_contribution, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                userId, "savings", "生活防衛資金（サンプル）", 600000, 240000,
                YearMonth.now().plusMonths(12).toString(), 30000, "active");
        jdbcTemplate.update(
                "INSERT INTO financial_goals (user_id, goal_type, name, target_amount, current_amount, "
                        + "target_month, monthly_contribution, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                userId, "asset_building", "将来資金（サンプル）", 3000000, 380000,
                YearMonth.now().plusMonths(36).toString(), 25000, "active");
        jdbcTemplate.update(
                "INSERT INTO life_plan_events (user_id, title, category, event_month, estimated_amount, "
                        + "entry_type, note) VALUES (?, ?, ?, ?, ?, ?, ?)",
                userId, "住み替え（サンプル）", "housing", YearMonth.now().plusMonths(18).toString(), 300000,
                "fact", "開発用テストデータ。本人入力の事実を登録する例です。");
        jdbcTemplate.update(
                "INSERT INTO life_plan_events (user_id, title, category, event_month, estimated_amount, "
                        + "entry_type, note) VALUES (?, ?, ?, ?, ?, ?, ?)",
                userId, "車の買い替え想定（サンプル）", "vehicle", YearMonth.now().plusMonths(36).toString(), 1200000,
                "assumption", "開発用テストデータ。仮定・予測の例です。");
    }
}