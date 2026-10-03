package com.aibot.travelplanner.trip;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Database mapping only; controllers continue returning the immutable Trip record. */
@Entity
@Table(name = "trips")
public class TripEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String destination;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(nullable = false)
    private int travelers;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal budget;

    @Column(nullable = false, length = 3)
    private String currency;

    @ElementCollection
    @CollectionTable(name = "trip_interests", joinColumns = @JoinColumn(name = "trip_id"))
    @OrderColumn(name = "interest_order")
    @Column(name = "interest", nullable = false, length = 80)
    private List<String> interests = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TripEntity() {
        // JPA uses this constructor when reading a stored row.
    }

    public TripEntity(String destination, LocalDate startDate, LocalDate endDate, int travelers,
                      BigDecimal budget, String currency, List<String> interests, Instant createdAt) {
        this.destination = destination;
        this.startDate = startDate;
        this.endDate = endDate;
        this.travelers = travelers;
        this.budget = budget;
        this.currency = currency;
        this.interests = new ArrayList<>(interests);
        this.createdAt = createdAt;
    }

    public Trip toTrip() {
        // Copy interests while the service transaction can still load the collection.
        return new Trip(id, destination, startDate, endDate, travelers, budget, currency,
                interests, createdAt);
    }
}
