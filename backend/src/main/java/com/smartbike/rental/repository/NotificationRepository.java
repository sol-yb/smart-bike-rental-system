package com.smartbike.rental.repository;

import com.smartbike.rental.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    List<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId);
    List<Notification> findByUserIdAndReadOrderByCreatedAtDesc(UUID userId, boolean read);
    List<Notification> findByUserIdIsNullOrderByCreatedAtDesc(); // Global / Admin notifications
}
