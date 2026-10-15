package com.masterjp.service;

import com.masterjp.domain.AuditLog;
import com.masterjp.repository.AuditLogRepository;
import org.springframework.stereotype.Service;

import java.util.Map;

/** Moi thao tac admin tren noi dung deu ghi audit_logs. */
@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void log(String adminId, String action, String targetType, String targetId,
                    Map<String, Object> diff) {
        AuditLog entry = new AuditLog();
        entry.setAdminId(adminId);
        entry.setAction(action);
        entry.setTargetType(targetType);
        entry.setTargetId(targetId);
        entry.setDiff(diff);
        auditLogRepository.save(entry);
    }
}
