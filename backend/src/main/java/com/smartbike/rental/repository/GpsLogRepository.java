package com.smartbike.rental.repository;

import com.smartbike.rental.model.GpsLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface GpsLogRepository extends JpaRepository<GpsLog, UUID> {
    List<GpsLog> findByBikeIdOrderByCreatedAtDesc(UUID bikeId);
    List<GpsLog> findByBikeIdAndCreatedAtBetweenOrderByCreatedAtAsc(
            UUID bikeId, 
            java.time.LocalDateTime start, 
            java.time.LocalDateTime end
    );
}
