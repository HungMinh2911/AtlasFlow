package com.atlasflow.workflow.api;

import com.atlasflow.security.tenant.RequireWorkflowAccess;
import com.atlasflow.workflow.application.WorkflowApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
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
    @ResponseStatus(HttpStatus.CREATED)
    public WorkflowResponse createWorkflow(
            @Valid @RequestBody CreateWorkflowRequest request
    ) {
        return workflowApplicationService.createWorkflow(request);
    }

    @GetMapping("/{workflowId}")
    @RequireWorkflowAccess(pathVariable = "workflowId")
    public WorkflowDetailResponse getWorkflow(
            @PathVariable("workflowId") UUID workflowId
    ) {
        return workflowApplicationService.getWorkflow(workflowId);
    }

    @PostMapping("/{workflowId}/publish")
    public WorkflowResponse publishWorkflow(@PathVariable("workflowId") UUID workflowId) {
        return workflowApplicationService.publishWorkflow(workflowId);
    }
}
