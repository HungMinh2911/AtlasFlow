package com.atlasflow.workflow.api;

import com.atlasflow.workflow.domain.Workflow;
import com.atlasflow.workflow.step.api.WorkflowStepResponse;

import java.util.List;
import java.util.UUID;

public record WorkflowResponse(
    UUID id, UUID project_id,
        int version, String status

) {
    public static WorkflowResponse from(Workflow workflow){
        return new WorkflowResponse(
                workflow.id(),
                workflow.project_id(),
                workflow.version(),
                workflow.status().name()
        );
    }
}
