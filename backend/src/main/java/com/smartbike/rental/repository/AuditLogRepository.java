package com.smartbike.rental.repository;

import com.smartbike.rental.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    List<AuditLog> findByUserIdOrderByCreatedAtDesc(UUID userId);
    List<AuditLog> findByEmailOrderByCreatedAtDesc(String email);
    List<AuditLog> findByActionOrderByCreatedAtDesc(String action);
}
