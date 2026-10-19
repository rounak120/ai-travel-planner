package com.aibot.travelplanner.trip;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Spring Data supplies save/findById and the implementation at application startup. */
public interface TripRepository extends JpaRepository<TripEntity, UUID> {
    org.springframework.data.domain.Page<TripEntity> findByOwnerName(String ownerName, org.springframework.data.domain.Pageable pageable);
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(value = "DELETE FROM trip_locations WHERE trip_id = :id", nativeQuery = true)
    void clearLocation(@org.springframework.data.repository.query.Param("id") UUID id);
}
