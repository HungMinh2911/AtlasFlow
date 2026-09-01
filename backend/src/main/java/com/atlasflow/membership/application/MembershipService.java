package com.atlasflow.membership.application;

import com.atlasflow.audit.application.AuditLogService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class MembershipService {

    private final JdbcClient jdbcClient;
    private final AuditLogService auditLogService;

    public MembershipService(JdbcClient jdbcClient, AuditLogService auditLogService) {
        this.jdbcClient = jdbcClient;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public void updateRole(UUID organizationId, UUID actorId, UUID targetUserId, UUID newRoleId, String requestId) {
        int updated = jdbcClient.sql("""
                UPDATE memberships
                SET role_id = :roleId
                WHERE user_id = :userId AND organization_id = :organizationId
                """)
                .param("roleId", newRoleId)
                .param("userId", targetUserId)
                .param("organizationId", organizationId)
                .update();

        if (updated == 0) {
            throw new IllegalArgumentException("Membership not found for user " + targetUserId + " in organization " + organizationId);
        }

        auditLogService.logMembershipRoleChanged(organizationId, actorId, targetUserId, requestId);
    }
}
