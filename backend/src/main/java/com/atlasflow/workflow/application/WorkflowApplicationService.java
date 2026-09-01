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
import java.util.Optional;
import java.util.UUID;

@Service
public class WorkflowApplicationService {

    private final ProjectRepository projectRepository;
    private final WorkflowRepository workflowRepository;
    private final WorkflowStepApplicationService workflowStepApplicationService;
    private final WorkflowCacheService workflowCacheService;


    public WorkflowApplicationService(
            ProjectRepository projectRepository,
            WorkflowRepository workflowRepository,
            WorkflowStepApplicationService workflowStepApplicationService,
            WorkflowCacheService workflowCacheService
    ) {
        this.projectRepository = projectRepository;
        this.workflowRepository = workflowRepository;
        this.workflowStepApplicationService = workflowStepApplicationService;
        this.workflowCacheService = workflowCacheService;
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
        Workflow savedWorkflow = workflowRepository.save(workflow);
        return WorkflowResponse.from(savedWorkflow);
    }

    public WorkflowDetailResponse getWorkflow(UUID workflowId) {

        return workflowCacheService.getWorkflowFromCache(workflowId)
                .orElseGet(() -> loadWorkflowAfterCacheMiss(workflowId));
    }

    private WorkflowDetailResponse loadWorkflowAfterCacheMiss(UUID workflowId) {
        Optional<String> lockToken = Optional.ofNullable(
                workflowCacheService.tryAcquireLoadLock(workflowId)
        ).orElseGet(Optional::empty);

        if (lockToken.isEmpty()) {
            WorkflowDetailResponse response = loadWorkflowFromDatabase(workflowId);
            workflowCacheService.putWorkflowToCache(response);
            return response;
        }

        try {
            Optional<WorkflowDetailResponse> secondRead =
                    workflowCacheService.getWorkflowFromCache(workflowId);
            if (secondRead.isPresent()) {
                return secondRead.get();
            }

            WorkflowDetailResponse response =
                    loadWorkflowFromDatabase(workflowId);
            workflowCacheService.putWorkflowToCache(response);
            return response;
        } finally {
            workflowCacheService.releaseLoadLock(workflowId, lockToken.get());
        }
    }

    private WorkflowDetailResponse loadWorkflowFromDatabase(UUID workflowId) {
        Workflow workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new WorkflowNotFoundException(workflowId));

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

    public WorkflowResponse publishWorkflow(UUID workflowId) {
        Workflow workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new WorkflowNotFoundException(workflowId));
        Workflow publishedWorkflow = workflowRepository.save(workflow.publish());
        workflowCacheService.evictWorkflowFromCache(workflowId);
        return WorkflowResponse.from(publishedWorkflow);
    }
}
