package com.atlasflow.workflow.step.api;

import com.atlasflow.workflow.step.domain.WorkflowStep;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

public record WorkflowStepResponse(
        UUID id,
        UUID workflow_id,
        int position,
        String task_type,
        JsonNode config_json
) {
    public static WorkflowStepResponse from(WorkflowStep step) {
        return new WorkflowStepResponse(
                step.id(),
                step.workflowId(),
                step.position(),
                step.taskType().name(),
                step.configJson()
        );
    }
}
