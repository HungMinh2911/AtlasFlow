package com.atlasflow.workflow.step.domain;

import tools.jackson.databind.JsonNode;

import java.util.UUID;

public record WorkflowStep(
        UUID id,
        UUID workflowId,
        int position,
        WorkflowStepType taskType,
        JsonNode configJson
) {
    public static WorkflowStep create(
            UUID id,
            UUID workflowId,
            int position,
            WorkflowStepType taskType,
            JsonNode configJson
    ) {
        return new WorkflowStep(
                id,
                workflowId,
                position,
                taskType,
                configJson
        );
    }
}
