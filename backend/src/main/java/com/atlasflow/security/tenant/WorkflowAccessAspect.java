package com.atlasflow.security.tenant;

import com.atlasflow.audit.application.AuditLogService;
import com.atlasflow.project.application.port.ProjectRepository;
import com.atlasflow.security.user.CustomUserDetails;
import com.atlasflow.workflow.application.port.WorkflowRepository;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Parameter;
import java.util.UUID;

@Aspect
@Component
public class WorkflowAccessAspect {

    private final WorkflowRepository workflowRepository;
    private final TenantMembershipChecker membershipChecker;
    private final AuditLogService auditLogService;
    private final ProjectRepository projectRepository;

    public WorkflowAccessAspect(WorkflowRepository workflowRepository, TenantMembershipChecker membershipChecker, AuditLogService auditLogService, ProjectRepository projectRepository) {
        this.workflowRepository = workflowRepository;
        this.membershipChecker = membershipChecker;
        this.auditLogService = auditLogService;
        this.projectRepository = projectRepository;
    }

    @Around("@annotation(requireWorkflowAccess)")
    public Object checkWorkflowAccess(
            ProceedingJoinPoint joinPoint,
            RequireWorkflowAccess requireWorkflowAccess

    ) throws Throwable {

        UUID wortflowId = extractPathVariable(joinPoint, requireWorkflowAccess.pathVariable());
        UUID userId = currentUserId();
        UUID projectId = workflowRepository.findById(wortflowId)
                .map(workflow -> workflow.project_id())
                .orElseThrow(() -> new AccessDeniedException("Workflow access denied"));
        UUID organizationId = projectRepository.findProjectById(projectId)
                .map(project -> project.organization_id())
                .orElseThrow(() -> new AccessDeniedException("Project access denied"));
        if (!membershipChecker.isMember(userId, organizationId)) {
            auditLogService.logUnauthorizedAccess(
                    organizationId, userId, "WORKFLOW", wortflowId, currentRequestId()
            );
            throw new AccessDeniedException("You are not a member of this project organization");


        }
        return joinPoint.proceed();
    }

        private UUID extractPathVariable (ProceedingJoinPoint joinPoint, String name){
            // MethodSignature in4 de mo ta 1 method
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Parameter[] parameters = signature.getMethod().getParameters();
            Object[] args = joinPoint.getArgs();
            for (int i = 0; i < parameters.length; i++) {
                // trong Parameter gom co annotation va  type
                PathVariable pathVariable = parameters[i].getAnnotation(PathVariable.class);
                if (pathVariable != null && name.equals(pathVariable.value())) {
                    return (UUID) args[i];
                }
            }
            throw new IllegalStateException("Workflow path variable not found: " + name);
        }

        private UUID currentUserId () {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails details)) {
                throw new AccessDeniedException("Authentication required");
            }
            return details.getId();
        }
        private String currentRequestId () {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attributes == null ? null : attributes.getRequest().getHeader("X-Request-Id");
        }

    }


