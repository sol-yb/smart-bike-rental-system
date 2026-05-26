package com.smartbike.rental.repository;

import com.smartbike.rental.model.GeofenceViolation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface GeofenceViolationRepository extends JpaRepository<GeofenceViolation, UUID> {
    List<GeofenceViolation> findByRideIdOrderByViolatedAtDesc(UUID rideId);
    List<GeofenceViolation> findByBikeIdOrderByViolatedAtDesc(UUID bikeId);
}
