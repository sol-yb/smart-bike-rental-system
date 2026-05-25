package com.smartbike.rental.repository;

import com.smartbike.rental.model.Bike;
import com.smartbike.rental.model.BikeState;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BikeRepository extends JpaRepository<Bike, UUID> {
    Optional<Bike> findByQrCode(String qrCode);
    List<Bike> findByState(BikeState state);
}
