package com.smartbike.rental.repository;

import com.smartbike.rental.model.Ride;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RideRepository extends JpaRepository<Ride, UUID> {
    List<Ride> findByUserId(UUID userId);
    List<Ride> findByActive(boolean active);
    Optional<Ride> findByUserIdAndActive(UUID userId, boolean active);
    Optional<Ride> findByBikeIdAndActive(UUID bikeId, boolean active);
}
