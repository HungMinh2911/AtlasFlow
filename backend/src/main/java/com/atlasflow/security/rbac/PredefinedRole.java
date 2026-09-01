package com.atlasflow.security.rbac;

import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

public enum PredefinedRole {
    ORGANIZATION_OWNER(Set.of(
            Permission.ORG_READ,
            Permission.ORG_UPDATE,
            Permission.MEMBER_INVITE,
            Permission.MEMBER_MANAGE,
            Permission.API_KEY_MANAGE,
            Permission.PROJECT_CREATE,
            Permission.PROJECT_READ,
            Permission.PROJECT_UPDATE,
            Permission.PROJECT_DELETE,
            Permission.WORKFLOW_CREATE,
            Permission.WORKFLOW_READ,
            Permission.WORKFLOW_UPDATE,
            Permission.WORKFLOW_PUBLISH,
            Permission.JOB_SUBMIT,
            Permission.JOB_READ,
            Permission.JOB_CANCEL,
            Permission.JOB_RETRY,
            Permission.FILE_UPLOAD,
            Permission.FILE_READ,
            Permission.AUDIT_LOG_READ
    )),

    PROJECT_OPERATOR(Set.of(
            Permission.ORG_READ,
            Permission.PROJECT_READ,
            Permission.WORKFLOW_CREATE,
            Permission.WORKFLOW_READ,
            Permission.WORKFLOW_UPDATE,
            Permission.WORKFLOW_PUBLISH,
            Permission.JOB_SUBMIT,
            Permission.JOB_READ,
            Permission.JOB_CANCEL,
            Permission.JOB_RETRY,
            Permission.FILE_UPLOAD,
            Permission.FILE_READ
    )),

    READ_ONLY_AUDITOR(Set.of(
            Permission.ORG_READ,
            Permission.PROJECT_READ,
            Permission.WORKFLOW_READ,
            Permission.JOB_READ,
            Permission.FILE_READ,
            Permission.AUDIT_LOG_READ
    )),

    WORKER_RUNTIME(Set.of(
            Permission.WORKFLOW_READ,
            Permission.JOB_READ,
            Permission.EXECUTION_UPDATE,
            Permission.FILE_READ,
            Permission.FILE_UPLOAD
    )),

    PLATFORM_OPERATOR(Set.of(
            Permission.values()
    ));

    private final Set<Permission> permissions;

    PredefinedRole(Set<Permission> permissions) {
        this.permissions = Collections.unmodifiableSet(permissions);
    }

    public Set<Permission> getPermissions() {
        return permissions;
    }

    /**
     * Converts role permissions into Spring Security GrantedAuthority collection.
     * Includes both permission strings (e.g. "job:submit") and the role name prefixed with "ROLE_".
     */
    public Set<SimpleGrantedAuthority> getAuthorities() {
        Set<SimpleGrantedAuthority> authorities = permissions.stream()
                .map(permission -> new SimpleGrantedAuthority(permission.getValue()))
                .collect(Collectors.toSet());
        authorities.add(new SimpleGrantedAuthority("ROLE_" + this.name()));
        return authorities;
    }
}
