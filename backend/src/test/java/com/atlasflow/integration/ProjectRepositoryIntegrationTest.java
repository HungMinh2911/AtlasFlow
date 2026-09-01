package com.atlasflow.integration;

import com.atlasflow.project.application.port.ProjectRepository;
import com.atlasflow.project.domain.Project;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectRepositoryIntegrationTest extends AbstractBaseIntegrationTest {

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldSaveAndFindProjectInTestcontainerPostgres() {
        UUID orgId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        // Seed org
        jdbcTemplate.update("INSERT INTO organizations (id, name) VALUES (?, ?)", orgId, "Org TC");

        Project project = Project.create(projectId, orgId, "TC Project");
        // Save project
        jdbcTemplate.update("INSERT INTO projects (id, organization_id, name, status) VALUES (?, ?, ?, ?)",
                project.id(), orgId, project.name(), project.status().name());

        Optional<Project> found = projectRepository.findProjectById(projectId);

        assertTrue(found.isPresent());
        assertEquals("TC Project", found.get().name());
    }
}
