package com.catsflow.catsflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.catsflow.catsflow.Service.DevPlanningSeedService;
import com.catsflow.catsflow.Service.PlanningDataService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
class PlanningDataServiceTest {

    @Autowired
    private PlanningDataService planningDataService;

    @Autowired
    private DevPlanningSeedService devPlanningSeedService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Transactional
    void createsAndUpdatesUserScopedPlanningData() {
        var profile = planningDataService.saveProfile(1L,
                new PlanningDataService.ProfileInput("renting", new BigDecimal("85000"), "stable",
                        new BigDecimal("10000"), "毎月の貯蓄を優先"));
        assertThat(profile.housingType()).isEqualTo("renting");
        assertThat(profile.monthlyLivingCost()).isEqualByComparingTo("85000");

        var liability = planningDataService.createLiability(1L,
                new PlanningDataService.LiabilityInput("奨学金", "student_loan", new BigDecimal("900000"),
                        LocalDate.of(2026, 11, 1), new BigDecimal("0.65"), new BigDecimal("12000"), 27, "本人入力"));
        assertThat(liability.id()).isPositive();
        assertThat(planningDataService.findLiabilities(2L)).noneMatch(row -> row.id().equals(liability.id()));
        assertThat(planningDataService.updateLiability(2L, liability.id(),
                new PlanningDataService.LiabilityInput("他人データ", "student_loan", BigDecimal.ONE,
                        LocalDate.now(), null, null, null, null)))
                .isEmpty();

        var goal = planningDataService.createGoal(1L,
                new PlanningDataService.GoalInput("savings", "生活防衛資金", new BigDecimal("600000"),
                        new BigDecimal("150000"), "2027-12", new BigDecimal("30000"), "active"));
        assertThat(goal.currentAmount()).isEqualByComparingTo("150000");
        assertThat(planningDataService.findGoals(1L)).extracting(PlanningDataService.FinancialGoal::id)
                .contains(goal.id());

        var event = planningDataService.createLifePlanEvent(1L,
                new PlanningDataService.LifeEventInput("転居", "housing", "2027-10",
                        new BigDecimal("300000"), "fact", "本人が予定"));
        assertThat(event.entryType()).isEqualTo("fact");
        assertThat(planningDataService.findLifePlanEvents(2L)).noneMatch(row -> row.id().equals(event.id()));
        assertThat(planningDataService.deleteLifePlanEvent(2L, event.id())).isFalse();
    }

    @Test
    @Transactional
    void rejectsNegativeAmountsAndInvalidEntryTypes() {
        assertThatThrownBy(() -> planningDataService.createLiability(1L,
                new PlanningDataService.LiabilityInput("loan", "student_loan", new BigDecimal("-1"),
                        LocalDate.now(), null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> planningDataService.createLifePlanEvent(1L,
                new PlanningDataService.LifeEventInput("event", "housing", "2027-04",
                        null, "guess", null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @Transactional
    void seedsNewDeveloperUserWithEditableTestData() {
        jdbcTemplate.update("INSERT INTO users (name, email, password_hash) VALUES (?, ?, ?)",
                "Dev Seed Test", "dev-seed-test@example.com", "test-hash");
        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = ?", Long.class, "dev-seed-test@example.com");
        assertThat(userId).isNotNull();

        devPlanningSeedService.seedForNewUser(userId);

        assertThat(planningDataService.findProfile(userId)).isPresent();
        assertThat(planningDataService.findLiabilities(userId)).hasSize(1);
        assertThat(planningDataService.findGoals(userId)).hasSize(2);
        assertThat(planningDataService.findLifePlanEvents(userId)).hasSize(2);
        assertThat(planningDataService.findGoals(userId))
                .allMatch(goal -> YearMonth.parse(goal.targetMonth()).isAfter(YearMonth.now()));
    }
}