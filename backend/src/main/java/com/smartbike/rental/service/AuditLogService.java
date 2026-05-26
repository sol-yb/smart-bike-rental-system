package com.smartbike.rental.service;

import com.smartbike.rental.model.AuditLog;
import com.smartbike.rental.repository.AuditLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
public class AuditLogService {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Transactional
    public void log(UUID userId, String email, String action, String ipAddress, String details) {
        AuditLog log = AuditLog.builder()
                .userId(userId)
                .email(email)
                .action(action)
                .ipAddress(ipAddress != null ? ipAddress : "SYSTEM")
                .details(details)
                .build();
        
        auditLogRepository.save(log);
        
        // Log to stdout/logging systems for monitoring
        System.out.printf("[AUDIT] Action: %s | User: %s | IP: %s | Details: %s%n", 
                action, email, ipAddress, details);
    }

    public List<AuditLog> getLogsByUserId(UUID userId) {
        return auditLogRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public List<AuditLog> getLogsByEmail(String email) {
        return auditLogRepository.findByEmailOrderByCreatedAtDesc(email);
    }

    public List<AuditLog> getAllLogs() {
        return auditLogRepository.findAll();
    }
}
