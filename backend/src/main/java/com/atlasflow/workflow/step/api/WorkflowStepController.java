package com.atlasflow.workflow.step.api;

import com.atlasflow.workflow.step.application.WorkflowStepApplicationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workflows/{workflowId}/steps")
public class WorkflowStepController {

    private final WorkflowStepApplicationService workflowStepApplicationService;

    public WorkflowStepController(
            WorkflowStepApplicationService workflowStepApplicationService
    ) {
        this.workflowStepApplicationService = workflowStepApplicationService;
    }

    @PostMapping
    public WorkflowStepResponse createWorkflowStep(
            @PathVariable UUID workflowId,
            @Valid @RequestBody CreateWorkflowStepRequest request
    ) {
        return workflowStepApplicationService.createWorkflowStep(
                workflowId,
                request
        );
    }
    @GetMapping
    public List<WorkflowStepResponse> getListWorkflowStep(@PathVariable UUID workflowId){
        return workflowStepApplicationService.responseList(workflowId);
    }
}
