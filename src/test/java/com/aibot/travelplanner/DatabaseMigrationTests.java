package com.aibot.travelplanner;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tests the actual PostgreSQL migration, not JPA repositories (which come next). */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DatabaseMigrationTests {
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void flywayAppliedVersionOne() {
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM flyway_schema_history
                WHERE version = '1' AND success = true
                """, Integer.class)).isEqualTo(1);
    }

    @Test
    void schemaStoresExactMoneyAndOrderedInterests() {
        UUID id = UUID.randomUUID();
        insertTrip(id, LocalDate.of(2027, 1, 14), new BigDecimal("50000.25"));
        jdbc.update("INSERT INTO trip_interests VALUES (?, ?, ?)", id, 0, "food");
        jdbc.update("INSERT INTO trip_interests VALUES (?, ?, ?)", id, 1, "history");

        assertThat(jdbc.queryForObject("SELECT budget FROM trips WHERE id = ?", BigDecimal.class, id))
                .isEqualByComparingTo("50000.25");
        assertThat(jdbc.queryForList("""
                SELECT interest FROM trip_interests WHERE trip_id = ? ORDER BY interest_order
                """, String.class, id)).containsExactly("food", "history");
    }

    @Test
    void databaseRejectsReversedDates() {
        assertThatThrownBy(() -> insertTrip(UUID.randomUUID(), LocalDate.of(2027, 1, 9),
                new BigDecimal("50000")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("trips_dates_in_order");
    }

    @Test
    void databaseRejectsNonpositiveBudget() {
        assertThatThrownBy(() -> insertTrip(UUID.randomUUID(), LocalDate.of(2027, 1, 14),
                BigDecimal.ZERO))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("trips_budget_positive");
    }

    @Test
    void interestMustBelongToAnExistingTrip() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO trip_interests VALUES (?, ?, ?)",
                UUID.randomUUID(), 0, "food"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("trip_interests_trip_fk");
    }

    private void insertTrip(UUID id, LocalDate endDate, BigDecimal budget) {
        jdbc.update("""
                INSERT INTO trips (id, destination, start_date, end_date, travelers, budget, currency, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, id, "Jaipur", LocalDate.of(2027, 1, 10), endDate, 2, budget, "INR",
                OffsetDateTime.now(ZoneOffset.UTC));
    }
}
