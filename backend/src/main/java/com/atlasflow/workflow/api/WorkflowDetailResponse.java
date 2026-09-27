package com.atlasflow.workflow.api;

import com.atlasflow.workflow.domain.Workflow;
import com.atlasflow.workflow.step.api.WorkflowStepResponse;

import java.util.List;
import java.util.UUID;

public record WorkflowDetailResponse(
        UUID id,
        UUID project_id,
        int version,
        String status,
        List<WorkflowStepResponse> steps
) {
    public static WorkflowDetailResponse from(Workflow workflow, List<WorkflowStepResponse> steps) {
        return new WorkflowDetailResponse(
                workflow.id(),
                workflow.project_id(),
                workflow.version(),
                workflow.status().name(),
                steps
        );
    }
}
