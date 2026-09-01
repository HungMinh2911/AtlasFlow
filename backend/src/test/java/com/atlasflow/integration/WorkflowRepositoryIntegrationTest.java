package com.atlasflow.integration;

import com.atlasflow.workflow.application.port.WorkflowRepository;
import com.atlasflow.workflow.domain.Workflow;
import com.atlasflow.workflow.domain.WorkflowStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowRepositoryIntegrationTest extends AbstractBaseIntegrationTest {

    @Autowired
    private WorkflowRepository workflowRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldSaveAndFindWorkflowInTestcontainerPostgres() {
        UUID orgId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID workflowId = UUID.randomUUID();

        jdbcTemplate.update("INSERT INTO organizations (id, name) VALUES (?, ?)", orgId, "Workflow Org TC");
        jdbcTemplate.update("INSERT INTO projects (id, organization_id, name, status) VALUES (?, ?, ?, 'ACTIVE')", projectId, orgId, "Workflow Proj TC");

        Workflow workflow = new Workflow(workflowId, projectId, 1, WorkflowStatus.PUBLISHED);
        workflowRepository.save(workflow);

        Optional<Workflow> found = workflowRepository.findById(workflowId);

        assertTrue(found.isPresent());
        assertEquals(WorkflowStatus.PUBLISHED, found.get().status());
    }
}
