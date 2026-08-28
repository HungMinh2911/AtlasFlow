package com.atlasflow.workflow.application;

import com.atlasflow.project.application.port.ProjectRepository;
import com.atlasflow.project.domain.ProjectNotExistsException;
import com.atlasflow.workflow.api.CreateWorkflowRequest;
import com.atlasflow.workflow.api.WorkflowDetailResponse;
import com.atlasflow.workflow.api.WorkflowResponse;
import com.atlasflow.workflow.application.port.WorkflowRepository;
import com.atlasflow.workflow.domain.Workflow;
import com.atlasflow.workflow.domain.WorkflowNotFoundException;
import com.atlasflow.workflow.step.api.WorkflowStepResponse;
import com.atlasflow.workflow.step.application.WorkflowStepApplicationService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class WorkflowApplicationService {

    private final ProjectRepository projectRepository;
    private final WorkflowRepository workflowRepository;
    private final WorkflowStepApplicationService workflowStepApplicationService;

    public WorkflowApplicationService(
            ProjectRepository projectRepository,
            WorkflowRepository workflowRepository,
            WorkflowStepApplicationService workflowStepApplicationService
    ) {
        this.projectRepository = projectRepository;
        this.workflowRepository = workflowRepository;
        this.workflowStepApplicationService = workflowStepApplicationService;
    }

    public WorkflowResponse createWorkflow(CreateWorkflowRequest request) {
        projectRepository.findProjectById(request.project_id())
                .orElseThrow(() ->
                        new ProjectNotExistsException(request.project_id())
                );

        int nextVersion = workflowRepository
                .findMaxVersionByProjectId(request.project_id())
                .map(version -> version + 1)
                .orElse(1);

        Workflow workflow = Workflow.create(
                UUID.randomUUID(),
                request.project_id(),
                nextVersion
        );

        return WorkflowResponse.from(workflowRepository.save(workflow));
    }

    public WorkflowDetailResponse getWorkflow(UUID workflowId) {
        Workflow workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() ->
                        new WorkflowNotFoundException(workflowId)
                );

        List<WorkflowStepResponse> steps =
                workflowStepApplicationService.responseList(workflowId);

        return new WorkflowDetailResponse(
                workflow.id(),
                workflow.project_id(),
                workflow.version(),
                workflow.status().name(),
                steps
        );
    }
}
