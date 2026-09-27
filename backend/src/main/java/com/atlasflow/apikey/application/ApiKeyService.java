package com.atlasflow.apikey.application;

import com.atlasflow.apikey.api.CreateApiKeyResponse;
import com.atlasflow.project.application.port.ProjectRepository;
import com.atlasflow.security.tenant.TenantMembershipChecker;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class ApiKeyService {
    private static final String PREFIX_PREFIX = "afk_";
    private final JdbcClient jdbcClient;
    private final ProjectRepository projectRepository;
    private final TenantMembershipChecker membershipChecker;
    private final SecureRandom secureRandom = new SecureRandom();

    public ApiKeyService(
            JdbcClient jdbcClient,
            ProjectRepository projectRepository,
            TenantMembershipChecker membershipChecker
    ) {
        this.jdbcClient = jdbcClient;
        this.projectRepository = projectRepository;
        this.membershipChecker = membershipChecker;
    }

    @Transactional
    public CreateApiKeyResponse create(UUID projectId, UUID userId) {
        requireProjectMember(projectId, userId);

        UUID id = UUID.randomUUID();
        String randomPart = randomToken(32);
        String secret = PREFIX_PREFIX + randomPart;
        String prefix = secret.substring(0, 12);
        String hash = sha256(secret);

        jdbcClient.sql("""
                INSERT INTO api_keys (id, project_id, prefix, secret_hash, last_used_at, revoked_at)
                VALUES (:id, :projectId, :prefix, :secretHash, NULL, NULL)
                """)
                .param("id", id)
                .param("projectId", projectId)
                .param("prefix", prefix)
                .param("secretHash", hash)
                .update();

        return new CreateApiKeyResponse(id, projectId, prefix, secret);
    }

    @Transactional
    public void revoke(UUID projectId, UUID keyId, UUID userId) {
        requireProjectMember(projectId, userId);
        int updated = jdbcClient.sql("""
                UPDATE api_keys
                SET revoked_at = COALESCE(revoked_at, CURRENT_TIMESTAMP)
                WHERE id = :id AND project_id = :projectId
                """)
                .param("id", keyId)
                .param("projectId", projectId)
                .update();
        if (updated == 0) {
            throw new IllegalArgumentException("API key not found for project");
        }
    }

    /**
     * Validates a key without ever returning its secret. Intended for service-to-service auth.
     */
    @Transactional
    public boolean authenticate(String rawSecret) {
        if (rawSecret == null || rawSecret.isBlank() || !rawSecret.startsWith(PREFIX_PREFIX)) {
            return false;
        }
        int updated = jdbcClient.sql("""
                UPDATE api_keys
                SET last_used_at = CURRENT_TIMESTAMP
                WHERE prefix = :prefix
                  AND secret_hash = :secretHash
                  AND revoked_at IS NULL
                """)
                .param("prefix", rawSecret.substring(0, Math.min(12, rawSecret.length())))
                .param("secretHash", sha256(rawSecret))
                .update();
        return updated == 1;
    }

    private void requireProjectMember(UUID projectId, UUID userId) {
        UUID organizationId = projectRepository.findProjectById(projectId)
                .map(project -> project.organization_id())
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));
        if (!membershipChecker.isMember(userId, organizationId)) {
            throw new AccessDeniedException("You are not a member of this project organization");
        }
    }

    private String randomToken(int bytes) {
        byte[] value = new byte[bytes];
        secureRandom.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
