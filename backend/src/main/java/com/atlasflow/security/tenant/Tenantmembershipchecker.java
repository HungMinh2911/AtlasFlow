package com.atlasflow.security.tenant;

import java.util.UUID;

public interface TenantMembershipChecker {
    boolean isMember(UUID userId, UUID organizationId);
}
