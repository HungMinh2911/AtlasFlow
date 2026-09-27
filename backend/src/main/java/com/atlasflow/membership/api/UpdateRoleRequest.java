package com.atlasflow.membership.api;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public class UpdateRoleRequest {

    @NotNull(message = "roleId không được để trống")
    private UUID roleId;

    public UpdateRoleRequest() {
    }

    public UpdateRoleRequest(UUID roleId) {
        this.roleId = roleId;
    }

    public UUID getRoleId() {
        return roleId;
    }

    public void setRoleId(UUID roleId) {
        this.roleId = roleId;
    }
}
