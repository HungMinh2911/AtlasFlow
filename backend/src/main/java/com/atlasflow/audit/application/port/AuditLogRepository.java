package com.atlasflow.audit.application.port;

import com.atlasflow.audit.domain.AuditLog;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface AuditLogRepository {

    AuditLog save(AuditLog auditLog);

    List<AuditLog> findByOrganizationId(UUID organizationId);

    List<AuditLog> findByAction(String action);

    long countRecentFailedLogins(UUID actorId, LocalDateTime since);
}
