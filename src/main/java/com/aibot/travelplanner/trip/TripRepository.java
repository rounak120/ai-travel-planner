package com.aibot.travelplanner.trip;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Spring Data supplies save/findById and the implementation at application startup. */
public interface TripRepository extends JpaRepository<TripEntity, UUID> {
}
