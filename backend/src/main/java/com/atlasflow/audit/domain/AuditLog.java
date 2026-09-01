package com.atlasflow.audit.domain;

import java.time.LocalDateTime;
import java.util.UUID;

public record AuditLog(
        UUID id,
        UUID organizationId,
        UUID actorId,
        String action,
        String resourceType,
        UUID resourceId,
        String requestId,
        LocalDateTime createdAt
) {
    public static AuditLog create(
            UUID organizationId,
            UUID actorId,
            String action,
            String resourceType,
            UUID resourceId,
            String requestId
    ) {
        return new AuditLog(
                UUID.randomUUID(),
                organizationId,
                actorId,
                action,
                resourceType,
                resourceId,
                requestId,
                LocalDateTime.now()
        );
    }
}
