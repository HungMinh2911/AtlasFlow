package com.atlasflow.membership.api;

import com.atlasflow.membership.application.MembershipService;
import com.atlasflow.security.tenant.RequireTenantAccess;
import com.atlasflow.security.user.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/memberships")
public class MembershipController {

    private final MembershipService membershipService;

    public MembershipController(MembershipService membershipService) {
        this.membershipService = membershipService;
    }

    @PutMapping("/{userId}/role")
    @RequireTenantAccess(pathVariable = "organizationId")
    public ResponseEntity<Map<String, String>> updateRole(
            @PathVariable("organizationId") UUID organizationId,
            @PathVariable("userId") UUID userId,
            @Valid @RequestBody UpdateRoleRequest request,
            HttpServletRequest servletRequest
    ) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        CustomUserDetails userDetails = (CustomUserDetails) auth.getPrincipal();
        String requestId = servletRequest.getHeader("X-Request-Id");

        membershipService.updateRole(organizationId, userDetails.getId(), userId, request.getRoleId(), requestId);
        return ResponseEntity.ok(Map.of("message", "Membership role updated successfully"));
    }
}
