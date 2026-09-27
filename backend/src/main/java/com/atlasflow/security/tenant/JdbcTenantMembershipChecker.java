package com.atlasflow.security.tenant;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class JdbcTenantMembershipChecker implements TenantMembershipChecker {

    private final JdbcClient jdbcClient;

    public JdbcTenantMembershipChecker(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public boolean isMember(UUID userId, UUID organizationId) {
        Boolean exists = jdbcClient.sql("""
                        SELECT EXISTS (
                            SELECT 1 FROM memberships
                            WHERE user_id = :userId
                              AND organization_id = :organizationId
                              AND status = 'ACTIVE'
                        )
                        """)
                .param("userId", userId)
                .param("organizationId", organizationId)
                .query(Boolean.class)
                .single();
        return Boolean.TRUE.equals(exists);
    }
}
