package com.atlasflow.security.rbac;

public enum Permission {
    // Org & Member
    ORG_READ("org:read"),
    ORG_UPDATE("org:update"),
    MEMBER_INVITE("member:invite"),
    MEMBER_MANAGE("member:manage"),
    API_KEY_MANAGE("api_key:manage"),

    // Project & Workflow
    PROJECT_CREATE("project:create"),
    PROJECT_READ("project:read"),
    PROJECT_UPDATE("project:update"),
    PROJECT_DELETE("project:delete"),
    WORKFLOW_CREATE("workflow:create"),
    WORKFLOW_READ("workflow:read"),
    WORKFLOW_UPDATE("workflow:update"),
    WORKFLOW_PUBLISH("workflow:publish"),

    // Job & Execution
    JOB_SUBMIT("job:submit"),
    JOB_READ("job:read"),
    JOB_CANCEL("job:cancel"),
    JOB_RETRY("job:retry"),
    EXECUTION_UPDATE("execution:update"),

    // File Storage
    FILE_UPLOAD("file:upload"),
    FILE_READ("file:read"),

    // Audit & Logs
    AUDIT_LOG_READ("audit:read");

    private final String value;

    Permission(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
