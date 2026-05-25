package com.smartbike.rental.repository;

import com.smartbike.rental.model.MqttEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface MqttEventRepository extends JpaRepository<MqttEvent, UUID> {
}
