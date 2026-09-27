package com.atlasflow.audit.domain;

public final class AuditAction {

    public static final String FAILED_LOGIN = "FAILED_LOGIN";
    public static final String MAX_LOGIN_ATTEMPTS_EXCEEDED = "MAX_LOGIN_ATTEMPTS_EXCEEDED";
    public static final String PASSWORD_CHANGED = "PASSWORD_CHANGED";
    public static final String MEMBERSHIP_ROLE_CHANGED = "MEMBERSHIP_ROLE_CHANGED";
    public static final String UNAUTHORIZED_ACCESS = "UNAUTHORIZED_ACCESS";
    public static final String TOKEN_REPLAY_ATTACK = "TOKEN_REPLAY_ATTACK";

    private AuditAction() {}
}
