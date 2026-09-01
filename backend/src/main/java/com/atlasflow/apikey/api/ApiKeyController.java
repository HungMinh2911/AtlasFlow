package com.atlasflow.apikey.api;

import com.atlasflow.apikey.application.ApiKeyService;
import com.atlasflow.security.user.CustomUserDetails;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/api-keys")
public class ApiKeyController {
    private final ApiKeyService apiKeyService;

    public ApiKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('api_key:manage')")
    public CreateApiKeyResponse create(
            @PathVariable UUID projectId,
            Authentication authentication
    ) {
        return apiKeyService.create(projectId, currentUserId(authentication));
    }

    @DeleteMapping("/{keyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('api_key:manage')")
    public void revoke(
            @PathVariable UUID projectId,
            @PathVariable UUID keyId,
            Authentication authentication
    ) {
        apiKeyService.revoke(projectId, keyId, currentUserId(authentication));
    }

    private UUID currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails details)) {
            throw new org.springframework.security.access.AccessDeniedException("Authentication required");
        }
        return details.getId();
    }
}
