package com.atlasflow.workflow.application;

import com.atlasflow.project.application.port.ProjectRepository;
import com.atlasflow.workflow.api.CreateWorkflowRequest;
import com.atlasflow.workflow.api.WorkflowResponse;
import com.atlasflow.workflow.application.port.WorkflowRepository;
import com.atlasflow.workflow.domain.Workflow;
import com.atlasflow.workflow.step.application.WorkflowStepApplicationService;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowApplicationServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private WorkflowRepository workflowRepository;

    @Mock
    private WorkflowStepApplicationService workflowStepService;

    @Test
    void createWorkflowShouldUseNextVersionForProject() {
        WorkflowApplicationService service = new WorkflowApplicationService(
                projectRepository,
                workflowRepository,
                workflowStepService
        );
        UUID projectId = UUID.randomUUID();

        when(projectRepository.findProjectById(projectId))
                .thenReturn(Optional.of(com.atlasflow.project.domain.Project.create(
                        UUID.randomUUID(), projectId, "AtlasFlow"
                )));
        when(workflowRepository.findMaxVersionByProjectId(projectId))
                .thenReturn(Optional.of(2));
        when(workflowRepository.save(any(Workflow.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WorkflowResponse response = service.createWorkflow(
                new CreateWorkflowRequest(projectId)
        );

        assertEquals(projectId, response.project_id());
        assertEquals(3, response.version());
        assertEquals("DRAFT", response.status());
    }
}
