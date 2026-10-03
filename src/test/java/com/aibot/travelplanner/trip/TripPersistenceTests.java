package com.aibot.travelplanner.trip;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class TripPersistenceTests {
    @Autowired
    private TripService service;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void readsStoredValuesAfterClearingJpaCache() {
        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            Trip created = service.create(request());
            entityManager.flush();
            entityManager.clear();

            Trip loaded = service.get(created.id());
            assertThat(loaded).isEqualTo(created);
            assertThat(loaded.interests()).containsExactly("history", "food", "history");
            transaction.setRollbackOnly();
        });
    }

    @Test
    void transactionFailureRollsBackBothTripAndInterests() {
        var createdId = new AtomicReference<UUID>();
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            createdId.set(service.create(request()).id());
            entityManager.flush();
            throw new IllegalStateException("Simulated failure after database inserts");
        })).isInstanceOf(IllegalStateException.class).hasMessageContaining("Simulated failure");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM trips WHERE id = ?",
                Integer.class, createdId.get())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM trip_interests WHERE trip_id = ?",
                Integer.class, createdId.get())).isZero();
    }

    private CreateTripRequest request() {
        return new CreateTripRequest("Jaipur", LocalDate.now().plusDays(30), LocalDate.now().plusDays(34),
                2, new BigDecimal("50000.25"), "INR", List.of("history", "food", "history"));
    }
}
