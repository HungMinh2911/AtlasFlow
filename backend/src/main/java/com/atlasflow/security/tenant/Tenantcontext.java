package com.atlasflow.security.tenant;

import java.util.UUID;

// Luu tru organizationId cua request hien tai trong suot vong doi thread xu ly
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setCurrentTenant(UUID organizationId) {
        CURRENT_TENANT.set(organizationId);
    }

    public static UUID getCurrentTenant() {
        return CURRENT_TENANT.get();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
