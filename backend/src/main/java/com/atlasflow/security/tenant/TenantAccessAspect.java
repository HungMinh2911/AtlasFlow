package com.atlasflow.security.tenant;

import com.atlasflow.security.user.CustomUserDetails;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.UUID;


// Tự động đánh chặn trước khi bất kỳ Controller method nào có @RequireTenantAccess được thực thi.

@Aspect
@Component
public class TenantAccessAspect {

    private static final Logger log = LoggerFactory.getLogger(TenantAccessAspect.class);

    private final TenantMembershipChecker membershipChecker;
    private final com.atlasflow.audit.application.AuditLogService auditLogService;

    public TenantAccessAspect(
            TenantMembershipChecker membershipChecker,
            com.atlasflow.audit.application.AuditLogService auditLogService
    ) {
        this.membershipChecker = membershipChecker;
        this.auditLogService = auditLogService;
    }

    @Around("@annotation(requireTenantAccess)")
    public Object checkTenantAccess(
            ProceedingJoinPoint joinPoint,
            RequireTenantAccess requireTenantAccess
    ) throws Throwable {
        UUID organizationId = extractOrganizationId(joinPoint, requireTenantAccess.pathVariable());
        UUID currentUserId = getCurrentUserId();

        if (!membershipChecker.isMember(currentUserId, organizationId)) {
            log.warn("Chặn truy cập cross-tenant: user {} không thuộc organization {}",
                    currentUserId, organizationId);
            // Ghi nhận Audit Log hành vi truy cập trái phép
            auditLogService.logUnauthorizedAccess(
                    organizationId,
                    currentUserId,
                    "ORGANIZATION",
                    organizationId,
                    currentRequestId()
            );
            throw new AccessDeniedException("Bạn không thuộc tổ chức này.");
        }

        TenantContext.setCurrentTenant(organizationId);
        try {
            return joinPoint.proceed();
        } finally {
            TenantContext.clear();
        }
    }

    private String currentRequestId() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes == null
                ? null
                : attributes.getRequest().getHeader("X-Request-Id");
    }

    private UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            throw new AccessDeniedException("Không xác định được người dùng hiện tại.");
        }
        return userDetails.getId();
    }

    private UUID extractOrganizationId(ProceedingJoinPoint joinPoint, String pathVariableName) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Parameter[] parameters = method.getParameters();
        Object[] args = joinPoint.getArgs();

        for (int i = 0; i < parameters.length; i++) {
            PathVariable pathVariable = parameters[i].getAnnotation(PathVariable.class);
            if (pathVariable != null && pathVariableName.equals(pathVariable.value())) {
                return (UUID) args[i];
            }
        }

        throw new IllegalStateException(
                "Không tìm thấy @PathVariable(\"" + pathVariableName + "\") trên method " +
                        method.getName() + "."
        );
    }
}
