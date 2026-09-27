package com.atlasflow.audit.application;

import com.atlasflow.audit.application.port.AuditLogRepository;
import com.atlasflow.audit.domain.AuditAction;
import com.atlasflow.audit.domain.AuditLog;
import com.atlasflow.audit.domain.AuditResourceType;
import com.atlasflow.users.application.port.UserRepository;
import com.atlasflow.users.domain.User;
import com.atlasflow.users.domain.UserStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int FAILED_LOGIN_WINDOW_MINUTES = 15;

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    public AuditLogService(AuditLogRepository auditLogRepository, UserRepository userRepository) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
    }

    /**
     * Ghi nhận audit log chung.
     * Sử dụng REQUIRES_NEW để việc ghi audit log không bị rollback nếu transaction nghiệp vụ gặp lỗi.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLog log(
            UUID organizationId,
            UUID actorId,
            String action,
            String resourceType,
            UUID resourceId,
            String requestId
    ) {
        AuditLog auditLog = AuditLog.create(organizationId, actorId, action, resourceType, resourceId, requestId);
        AuditLog saved = auditLogRepository.save(auditLog);
        log.info("AUDIT: [action={}, org={}, actor={}, resource={}:{}, reqId={}]",
                action, organizationId, actorId, resourceType, resourceId, requestId);
        return saved;
    }

    /**
     * Ghi nhận đăng nhập sai và tự động khóa tài khoản khi vượt quá số lần cho phép.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean logFailedLogin(String email, String requestId) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        UUID actorId = userOpt.map(User::id).orElse(null);

        // 1. Ghi log FAILED_LOGIN
        log(null, actorId, AuditAction.FAILED_LOGIN, AuditResourceType.AUTH, actorId, requestId);

        // 2. Nếu tìm thấy user, đếm số lần sai trong 15 phút gần nhất
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            long recentFails = auditLogRepository.countRecentFailedLogins(
                    user.id(),
                    LocalDateTime.now().minusMinutes(FAILED_LOGIN_WINDOW_MINUTES)
            );

            if (recentFails >= MAX_FAILED_ATTEMPTS && user.status() != UserStatus.LOCKED) {
                // Khóa tài khoản
                User lockedUser = new User(
                        user.id(),
                        user.email(),
                        user.passwordHash(),
                        UserStatus.LOCKED,
                        user.createdAt()
                );
                userRepository.save(lockedUser);

                // Ghi log MAX_LOGIN_ATTEMPTS_EXCEEDED
                log(null, user.id(), AuditAction.MAX_LOGIN_ATTEMPTS_EXCEEDED, AuditResourceType.USER, user.id(), requestId);
                log.warn("Tài khoản {} đã bị KHÓA do đăng nhập sai liên tiếp {} lần!", email, recentFails);
                return true;
            }
        }
        return false;
    }

    /**
     * Ghi nhận hành vi đổi mật khẩu.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logPasswordChanged(UUID userId, String requestId) {
        log(null, userId, AuditAction.PASSWORD_CHANGED, AuditResourceType.USER, userId, requestId);
    }

    /**
     * Ghi nhận hành vi đổi quyền membership trong tổ chức.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logMembershipRoleChanged(
            UUID organizationId,
            UUID actorId,
            UUID targetUserId,
            String requestId
    ) {
        log(organizationId, actorId, AuditAction.MEMBERSHIP_ROLE_CHANGED, AuditResourceType.MEMBERSHIP, targetUserId, requestId);
    }

    /**
     * Ghi nhận hành vi truy cập trái phép (cross-tenant IDOR hoặc thiếu quyền).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logUnauthorizedAccess(
            UUID organizationId,
            UUID actorId,
            String resourceType,
            UUID resourceId,
            String requestId
    ) {
        log(organizationId, actorId, AuditAction.UNAUTHORIZED_ACCESS, resourceType, resourceId, requestId);
    }

    /**
     * Ghi nhận tấn công Token Replay Attack.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logTokenReplay(UUID userId, String requestId) {
        log(null, userId, AuditAction.TOKEN_REPLAY_ATTACK, AuditResourceType.AUTH, userId, requestId);
    }
}
