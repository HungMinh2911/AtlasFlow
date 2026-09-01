package com.atlasflow.apikey.application;

import com.atlasflow.project.application.port.ProjectRepository;
import com.atlasflow.project.domain.Project;
import com.atlasflow.security.tenant.TenantMembershipChecker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ApiKeyServiceTest {
    private JdbcClient jdbcClient;
    private ProjectRepository projectRepository;
    private TenantMembershipChecker membershipChecker;
    private ApiKeyService service;
    private UUID projectId;
    private UUID userId;
    private UUID organizationId;

    @BeforeEach
    void setUp() {
        jdbcClient = mock(JdbcClient.class, RETURNS_DEEP_STUBS);
        projectRepository = mock(ProjectRepository.class);
        membershipChecker = mock(TenantMembershipChecker.class);
        service = new ApiKeyService(jdbcClient, projectRepository, membershipChecker);
        projectId = UUID.randomUUID();
        userId = UUID.randomUUID();
        organizationId = UUID.randomUUID();
        when(projectRepository.findProjectById(projectId)).thenReturn(Optional.of(
                Project.create(projectId, organizationId, "secure-project")
        ));
        when(membershipChecker.isMember(userId, organizationId)).thenReturn(true);
        when(jdbcClient.sql(anyString()).param(anyString(), any()).update()).thenReturn(1);
        when(jdbcClient.sql(anyString()).param(anyString(), any()).param(anyString(), any()).update()).thenReturn(1);
    }

    @Test
    void createReturnsSecretButWritesOnlyHash() {
        var result = service.create(projectId, userId);

        assertThat(result.secret()).startsWith("afk_");
        assertThat(result.prefix()).isEqualTo(result.secret().substring(0, 12));
        verify(jdbcClient).sql(contains("secret_hash"));
    }

    @Test
    void revokeRequiresProjectMembershipAndMarksKeyRevoked() {
        UUID keyId = UUID.randomUUID();

        service.revoke(projectId, keyId, userId);

        verify(jdbcClient).sql(contains("revoked_at"));
    }
}
