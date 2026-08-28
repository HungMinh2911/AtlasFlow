package com.atlasflow.workflow.step.api;

import com.atlasflow.workflow.step.domain.WorkflowStepType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

public record CreateWorkflowStepRequest(
        @Min(value = 1, message = "position must be greater than 0")
        int position,

        @NotNull
        WorkflowStepType task_type,

        @NotNull
        JsonNode config_json
) {
}
