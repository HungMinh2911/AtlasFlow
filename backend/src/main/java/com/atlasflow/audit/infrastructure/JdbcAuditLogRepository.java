package com.atlasflow.audit.infrastructure;

import com.atlasflow.audit.application.port.AuditLogRepository;
import com.atlasflow.audit.domain.AuditAction;
import com.atlasflow.audit.domain.AuditLog;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Repository
@Primary
public class JdbcAuditLogRepository implements AuditLogRepository {

    private final JdbcClient jdbcClient;

    public JdbcAuditLogRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    private AuditLog mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp timestamp = rs.getTimestamp("created_at");
        return new AuditLog(
                rs.getObject("id", UUID.class),
                rs.getObject("organization_id", UUID.class),
                rs.getObject("actor_id", UUID.class),
                rs.getString("action"),
                rs.getString("resource_type"),
                rs.getObject("resource_id", UUID.class),
                rs.getString("request_id"),
                timestamp != null
                        ? timestamp.toInstant().atZone(ZoneOffset.UTC).toLocalDateTime()
                        : null
        );
    }

    @Override
    public AuditLog save(AuditLog auditLog) {
        String sql = """
                INSERT INTO audit_logs (id, organization_id, actor_id, action, resource_type, resource_id, request_id, created_at)
                VALUES (:id, :organizationId, :actorId, :action, :resourceType, :resourceId, :requestId, :createdAt)
                """;

        jdbcClient.sql(sql)
                .param("id", auditLog.id())
                .param("organizationId", auditLog.organizationId())
                .param("actorId", auditLog.actorId())
                .param("action", auditLog.action())
                .param("resourceType", auditLog.resourceType())
                .param("resourceId", auditLog.resourceId())
                .param("requestId", auditLog.requestId())
                .param("createdAt", auditLog.createdAt() != null ? Timestamp.valueOf(auditLog.createdAt()) : Timestamp.valueOf(LocalDateTime.now()))
                .update();

        return auditLog;
    }

    @Override
    public List<AuditLog> findByOrganizationId(UUID organizationId) {
        String sql = """
                SELECT id, organization_id, actor_id, action, resource_type, resource_id, request_id, created_at
                FROM audit_logs
                WHERE organization_id = :organizationId
                ORDER BY created_at DESC
                """;

        return jdbcClient.sql(sql)
                .param("organizationId", organizationId)
                .query(this::mapRow)
                .list();
    }

    @Override
    public List<AuditLog> findByAction(String action) {
        String sql = """
                SELECT id, organization_id, actor_id, action, resource_type, resource_id, request_id, created_at
                FROM audit_logs
                WHERE action = :action
                ORDER BY created_at DESC
                """;

        return jdbcClient.sql(sql)
                .param("action", action)
                .query(this::mapRow)
                .list();
    }

    @Override
    public long countRecentFailedLogins(UUID actorId, LocalDateTime since) {
        String sql = """
                SELECT COUNT(*)
                FROM audit_logs
                WHERE actor_id = :actorId
                  AND action = :action
                  AND created_at >= :since
                """;

        return jdbcClient.sql(sql)
                .param("actorId", actorId)
                .param("action", AuditAction.FAILED_LOGIN)
                .param("since", Timestamp.valueOf(since))
                .query(Long.class)
                .single();
    }
}
