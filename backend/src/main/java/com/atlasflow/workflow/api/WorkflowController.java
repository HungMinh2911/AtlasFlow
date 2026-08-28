package com.atlasflow.workflow.api;

import com.atlasflow.workflow.application.WorkflowApplicationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workflows")
public class WorkflowController {

    private final WorkflowApplicationService workflowApplicationService;

    public WorkflowController(
            WorkflowApplicationService workflowApplicationService
    ) {
        this.workflowApplicationService = workflowApplicationService;
    }

    @PostMapping
    public WorkflowResponse createWorkflow(
            @Valid @RequestBody CreateWorkflowRequest request
    ) {
        return workflowApplicationService.createWorkflow(request);
    }

    @GetMapping("/{workflowId}")
    public WorkflowDetailResponse getWorkflow(
            @PathVariable UUID workflowId
    ) {
        return workflowApplicationService.getWorkflow(workflowId);
    }
}
